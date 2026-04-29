package br.com.stockshift.service.audit;

import br.com.stockshift.dto.audit.AuditEventFilter;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.AuditEvent;
import br.com.stockshift.repository.AuditEventRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.security.UserPrincipal;
import br.com.stockshift.security.WarehouseContext;
import br.com.stockshift.security.audit.AuditContext;
import br.com.stockshift.security.audit.AuditContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository repository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        WarehouseContext.clear();
        AuditContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordShouldMergeRequestContextTenantPrincipalAndWarehouse() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        WarehouseContext.setWarehouseId(warehouseId);
        UserPrincipal principal = new UserPrincipal(actorId, tenantId, "actor@example.com", "pwd",
                true, List.of(), Set.of(warehouseId), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "pwd", principal.getAuthorities()));
        AuditContextHolder.set(AuditContext.builder()
                .requestId("req-1")
                .httpMethod("POST")
                .httpPath("/api/test")
                .httpStatus(201)
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .build());
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuditService service = new AuditService(repository);

        AuditEvent saved = service.record(AuditEventCreateRequest.builder()
                .operation(AuditService.OPERATION_BUSINESS)
                .action("CREATED")
                .outcome(AuditService.OUTCOME_SUCCESS)
                .resourceType("TEST")
                .resourceId("1")
                .beforeState(Map.of("old", true))
                .afterState(Map.of("new", true))
                .changedFields(List.of("field"))
                .metadata(Map.of("source", "test"))
                .build());

        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getActorUserId()).isEqualTo(actorId);
        assertThat(saved.getActorEmail()).isEqualTo("actor@example.com");
        assertThat(saved.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(saved.getRequestId()).isEqualTo("req-1");
        assertThat(saved.getHttpStatus()).isEqualTo(201);
    }

    @Test
    void findEventsShouldFallbackToDefaultSortWhenSwaggerSendsDirectionOnly() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        AuditService service = new AuditService(repository);
        Pageable badSwaggerPageable = PageRequest.of(0, 1, Sort.by("ASC"));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.findEvents(AuditEventFilter.builder().action("BATCH_CREATED").build(), badSwaggerPageable);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("occurredAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void findEventsShouldMapResponsesUseAllowedSortAndRequireTenant() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .occurredAt(LocalDateTime.now())
                .actorEmail("actor@example.com")
                .operation(AuditService.OPERATION_SECURITY)
                .action("LOGIN")
                .outcome(AuditService.OUTCOME_SUCCESS)
                .resourceType("AUTH")
                .resourceId("1")
                .reason("ok")
                .httpStatus(200)
                .metadata(Map.of("k", "v"))
                .build();
        AuditService service = new AuditService(repository);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        var page = service.findResourceEvents("AUTH", "1",
                PageRequest.of(0, 10, Sort.by("actorEmail")));

        assertThat(page.getContent()).singleElement().satisfies(response -> {
            assertThat(response.actorEmail()).isEqualTo("actor@example.com");
            assertThat(response.operation()).isEqualTo(AuditService.OPERATION_SECURITY);
            assertThat(response.metadata()).containsEntry("k", "v");
        });

        TenantContext.clear();
        assertThatThrownBy(() -> service.findEvents(AuditEventFilter.builder().build(), Pageable.unpaged()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void findEventsForExportShouldValidateDatesAndNormalizeLimit() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        AuditService service = new AuditService(repository);
        AuditEventFilter filter = AuditEventFilter.builder()
                .dateFrom(LocalDateTime.now().minusDays(1))
                .dateTo(LocalDateTime.now())
                .build();
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(AuditEvent.builder().tenantId(tenantId).build())));

        assertThat(service.findEventsForExport(filter, 0)).hasSize(1);
        assertThat(service.findEventsForExport(filter, 20_000)).hasSize(1);

        assertThatThrownBy(() -> service.findEventsForExport(AuditEventFilter.builder().build(), 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dateFrom and dateTo");
    }
}
