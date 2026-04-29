package br.com.stockshift.service.audit;

import br.com.stockshift.dto.audit.AuditEventFilter;
import br.com.stockshift.model.entity.AuditEvent;
import br.com.stockshift.repository.AuditEventRepository;
import br.com.stockshift.security.TenantContext;
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

import java.util.List;
import java.util.UUID;

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
}
