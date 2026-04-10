package br.com.stockshift.service;

import br.com.stockshift.dto.warehouse.WarehouseStockSummaryProjection;
import br.com.stockshift.dto.warehouse.WarehouseStockSummaryResponse;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private WarehouseAccessService warehouseAccessService;

    @InjectMocks
    private WarehouseService warehouseService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
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
}
