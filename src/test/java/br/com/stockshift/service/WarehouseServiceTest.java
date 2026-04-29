package br.com.stockshift.service;

import br.com.stockshift.dto.warehouse.WarehouseRequest;
import br.com.stockshift.dto.warehouse.WarehouseStockSummaryProjection;
import br.com.stockshift.dto.warehouse.WarehouseStockSummaryResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.audit.AuditService;
import br.com.stockshift.service.audit.AuditSnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private WarehouseAccessService warehouseAccessService;

    @Mock
    private AuditService auditService;

    @Mock
    private AuditSnapshotService auditSnapshotService;

    @InjectMocks
    private WarehouseService warehouseService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        when(auditSnapshotService.snapshot(any())).thenReturn(Map.of("id", "value"));
        when(auditSnapshotService.diff(any(), any())).thenReturn(List.of("name"));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> {
            Warehouse warehouse = invocation.getArgument(0);
            if (warehouse.getId() == null) {
                warehouse.setId(UUID.randomUUID());
            }
            return warehouse;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldReturnStockSummariesForAssignedWarehouses() {
        UUID firstWarehouseId = UUID.randomUUID();
        UUID secondWarehouseId = UUID.randomUUID();

        when(warehouseAccessService.hasFullAccess()).thenReturn(false);
        when(warehouseAccessService.getUserWarehouseIds()).thenReturn(Set.of(firstWarehouseId, secondWarehouseId));
        when(batchRepository.findStockSummaryByWarehouseIds(tenantId, Set.of(firstWarehouseId, secondWarehouseId)))
                .thenReturn(List.of(
                        summary(firstWarehouseId, 3L, 5L, new BigDecimal("120.000")),
                        summary(secondWarehouseId, 1L, 2L, new BigDecimal("8.000"))
                ));

        List<WarehouseStockSummaryResponse> response = warehouseService.getStockSummaries();

        assertThat(response).hasSize(2);
        assertThat(response)
                .extracting(WarehouseStockSummaryResponse::getWarehouseId)
                .containsExactlyInAnyOrder(firstWarehouseId, secondWarehouseId);
        assertThat(response)
                .filteredOn(item -> item.getWarehouseId().equals(firstWarehouseId))
                .first()
                .satisfies(item -> {
                    assertThat(item.getProductCount()).isEqualTo(3L);
                    assertThat(item.getBatchCount()).isEqualTo(5L);
                    assertThat(item.getTotalQuantity()).isEqualByComparingTo("120.000");
                });

        verify(warehouseRepository, never()).findAllByTenantId(tenantId);
    }

    @Test
    void shouldReturnStockSummariesForAllTenantWarehousesWhenUserHasFullAccess() {
        UUID firstWarehouseId = UUID.randomUUID();
        UUID secondWarehouseId = UUID.randomUUID();

        Warehouse firstWarehouse = new Warehouse();
        firstWarehouse.setId(firstWarehouseId);

        Warehouse secondWarehouse = new Warehouse();
        secondWarehouse.setId(secondWarehouseId);

        when(warehouseAccessService.hasFullAccess()).thenReturn(true);
        when(warehouseRepository.findAllByTenantId(tenantId)).thenReturn(List.of(firstWarehouse, secondWarehouse));
        when(batchRepository.findStockSummaryByWarehouseIds(tenantId, Set.of(firstWarehouseId, secondWarehouseId)))
                .thenReturn(List.of(summary(firstWarehouseId, 2L, 4L, new BigDecimal("44.000"))));

        List<WarehouseStockSummaryResponse> response = warehouseService.getStockSummaries();

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.getWarehouseId()).isEqualTo(firstWarehouseId);
            assertThat(item.getProductCount()).isEqualTo(2L);
            assertThat(item.getBatchCount()).isEqualTo(4L);
            assertThat(item.getTotalQuantity()).isEqualByComparingTo("44.000");
        });
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoAccessibleWarehouses() {
        when(warehouseAccessService.hasFullAccess()).thenReturn(false);
        when(warehouseAccessService.getUserWarehouseIds()).thenReturn(Set.of());

        List<WarehouseStockSummaryResponse> response = warehouseService.getStockSummaries();

        assertThat(response).isEmpty();
        verify(batchRepository, never()).findStockSummaryByWarehouseIds(tenantId, Set.of());
    }

    @Test
    void shouldCreateFindUpdateAndDeleteWarehouses() {
        Warehouse existing = warehouse("Main", "MA-RE");
        Warehouse updated = warehouse("Updated", "UP-RE");
        WarehouseRequest createRequest = request("Main", null);
        when(warehouseRepository.findAllByTenantId(tenantId)).thenReturn(List.of(existing));
        when(warehouseRepository.findByTenantIdAndName(tenantId, "Main")).thenReturn(Optional.empty());
        when(warehouseRepository.findByTenantIdAndCode(tenantId, "MAI-RE")).thenReturn(Optional.empty());

        assertThat(warehouseService.create(createRequest).getCode()).startsWith("MAI-RE");

        when(warehouseAccessService.hasFullAccess()).thenReturn(true);
        when(warehouseRepository.findAllByTenantId(tenantId)).thenReturn(List.of(existing, updated));
        assertThat(warehouseService.findAll()).extracting("name").containsExactly("Main", "Updated");

        when(warehouseRepository.findByTenantIdAndId(tenantId, existing.getId())).thenReturn(Optional.of(existing));
        assertThat(warehouseService.findById(existing.getId()).getName()).isEqualTo("Main");

        when(warehouseRepository.findByTenantIdAndIsActive(tenantId, true)).thenReturn(List.of(existing));
        assertThat(warehouseService.findActive(true)).singleElement().extracting("name").isEqualTo("Main");

        WarehouseRequest updateRequest = request("Updated", "UP-RE");
        when(warehouseRepository.findByTenantIdAndName(tenantId, "Updated")).thenReturn(Optional.empty());
        when(warehouseRepository.findByTenantIdAndCode(tenantId, "UP-RE")).thenReturn(Optional.empty());
        assertThat(warehouseService.update(existing.getId(), updateRequest).getName()).isEqualTo("Updated");

        warehouseService.delete(existing.getId());
        verify(warehouseRepository).delete(existing);
        verify(auditService, org.mockito.Mockito.atLeastOnce()).record(any());
    }

    @Test
    void shouldRejectDuplicateWarehouseNameAndCode() {
        Warehouse existing = warehouse("Main", "MAIN");
        when(warehouseRepository.findByTenantIdAndName(tenantId, "Main")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> warehouseService.create(request("Main", "MAIN")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("name");

        when(warehouseRepository.findByTenantIdAndName(tenantId, "Other")).thenReturn(Optional.empty());
        when(warehouseRepository.findByTenantIdAndCode(tenantId, "MAIN")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> warehouseService.create(request("Other", "MAIN")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("code");
    }

    private WarehouseStockSummaryProjection summary(
            UUID warehouseId,
            long productCount,
            long batchCount,
            BigDecimal totalQuantity
    ) {
        return new WarehouseStockSummaryProjection() {
            @Override
            public UUID getWarehouseId() {
                return warehouseId;
            }

            @Override
            public long getProductCount() {
                return productCount;
            }

            @Override
            public long getBatchCount() {
                return batchCount;
            }

            @Override
            public BigDecimal getTotalQuantity() {
                return totalQuantity;
            }
        };
    }

    private Warehouse warehouse(String name, String code) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setTenantId(tenantId);
        warehouse.setName(name);
        warehouse.setCode(code);
        warehouse.setCity("Recife");
        warehouse.setState("PE");
        warehouse.setAddress("Street");
        warehouse.setIsActive(true);
        return warehouse;
    }

    private WarehouseRequest request(String name, String code) {
        return WarehouseRequest.builder()
                .name(name)
                .code(code)
                .city("Recife")
                .state("PE")
                .address("Street")
                .isActive(true)
                .build();
    }
}
