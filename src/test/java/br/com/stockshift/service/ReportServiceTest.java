package br.com.stockshift.service;

import br.com.stockshift.dto.report.DashboardResponse;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.entity.StockMovementItem;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.StockMovementRepository;
import br.com.stockshift.repository.TransferRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private WarehouseAccessService warehouseAccessService;

    @Mock
    private PermissionResolverService permissionResolverService;

    @InjectMocks
    private ReportService reportService;

    private UUID tenantId;
    private UUID warehouseId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldBuildRichDashboardPayloadForDashboardScope() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setName("Depósito Central");
        warehouse.setIsActive(true);

        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Eletrônicos");

        Product activeProduct = new Product();
        activeProduct.setId(UUID.randomUUID());
        activeProduct.setActive(true);
        activeProduct.setCategory(category);

        Product inactiveProduct = new Product();
        inactiveProduct.setId(UUID.randomUUID());
        inactiveProduct.setActive(false);
        inactiveProduct.setCategory(category);

        Batch firstBatch = new Batch();
        firstBatch.setId(UUID.randomUUID());
        firstBatch.setWarehouse(warehouse);
        firstBatch.setProduct(activeProduct);
        firstBatch.setQuantity(new BigDecimal("10"));
        firstBatch.setCostPrice(2500L);
        firstBatch.setExpirationDate(LocalDateTime.now().toLocalDate().plusDays(5));

        Batch secondBatch = new Batch();
        secondBatch.setId(UUID.randomUUID());
        secondBatch.setWarehouse(warehouse);
        secondBatch.setProduct(inactiveProduct);
        secondBatch.setQuantity(new BigDecimal("3"));
        secondBatch.setCostPrice(1000L);

        StockMovement movement = new StockMovement();
        movement.setId(UUID.randomUUID());
        movement.setType(StockMovementType.PURCHASE_IN);
        movement.setDirection(MovementDirection.IN);
        movement.setNotes("Reposição");
        movement.setCreatedAt(LocalDateTime.now().minusHours(2));
        movement.setItems(List.of(
                StockMovementItem.builder().productId(activeProduct.getId()).productName("A").build(),
                StockMovementItem.builder().productId(inactiveProduct.getId()).productName("B").build()
        ));

        when(warehouseAccessService.hasFullAccess()).thenReturn(true);
        when(batchRepository.findAllByTenantId(tenantId)).thenReturn(List.of(firstBatch, secondBatch));
        when(warehouseRepository.findAllByTenantId(tenantId)).thenReturn(List.of(warehouse));
        when(stockMovementRepository.findWithFilters(
                eq(tenantId),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(PageRequest.of(0, 20))
        )).thenReturn(new PageImpl<>(List.of(movement)));
        when(stockMovementRepository.countMovementsByTypeAndPeriod(eq(tenantId), eq(null), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(
                        List.<Object[]>of(new Object[]{StockMovementType.PURCHASE_IN, 2L}),
                        List.<Object[]>of(new Object[]{StockMovementType.TRANSFER_OUT, 1L}),
                        List.<Object[]>of(new Object[]{StockMovementType.ADJUSTMENT_IN, 4L})
                );
        when(stockMovementRepository.countDistinctTransferReferencesByPeriod(
                eq(tenantId),
                eq(null),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(Set.of(StockMovementType.TRANSFER_IN, StockMovementType.TRANSFER_OUT))
        )).thenReturn(1L);

        DashboardResponse response = reportService.getDashboard();

        assertThat(response.getTotalProducts()).isEqualTo(2L);
        assertThat(response.getActiveProducts()).isEqualTo(1L);
        assertThat(response.getTotalWarehouses()).isEqualTo(1L);
        assertThat(response.getActiveWarehouses()).isEqualTo(1L);
        assertThat(response.getTotalBatches()).isEqualTo(2L);
        assertThat(response.getLowStockCount()).isEqualTo(2L);
        assertThat(response.getExpiringCount()).isEqualTo(1L);
        assertThat(response.getTotalStockValue()).isEqualByComparingTo("280.00");
        assertThat(response.getStockByWarehouse()).singleElement().satisfies(item -> {
            assertThat(item.getWarehouseName()).isEqualTo("Depósito Central");
            assertThat(item.getProductCount()).isEqualTo(2L);
            assertThat(item.getBatchCount()).isEqualTo(2L);
            assertThat(item.getStockValue()).isEqualByComparingTo("280.00");
        });
        assertThat(response.getStockByCategory()).singleElement().satisfies(item -> {
            assertThat(item.getCategoryName()).isEqualTo("Eletrônicos");
            assertThat(item.getProductCount()).isEqualTo(2L);
        });
        assertThat(response.getRecentMovements()).singleElement().satisfies(item -> {
            assertThat(item.getMovementType()).isEqualTo("ENTRY");
            assertThat(item.getProductCount()).isEqualTo(2);
            assertThat(item.getStatus()).isEqualTo("COMPLETED");
        });
        assertThat(response.getMovementStats().getToday().getEntries()).isEqualTo(2L);
        assertThat(response.getMovementStats().getThisWeek().getTransfers()).isEqualTo(1L);
        assertThat(response.getMovementStats().getThisMonth().getAdjustments()).isEqualTo(4L);
    }

    @Test
    void shouldDeduplicateTransferMovementsInDashboardTimelineAndStats() {
        Warehouse sourceWarehouse = new Warehouse();
        sourceWarehouse.setId(warehouseId);
        sourceWarehouse.setName("Depósito Central");
        sourceWarehouse.setIsActive(true);

        Warehouse destinationWarehouse = new Warehouse();
        destinationWarehouse.setId(UUID.randomUUID());
        destinationWarehouse.setName("Filial Sul");
        destinationWarehouse.setIsActive(true);

        UUID transferReferenceId = UUID.randomUUID();

        StockMovement transferOut = new StockMovement();
        transferOut.setId(UUID.randomUUID());
        transferOut.setType(StockMovementType.TRANSFER_OUT);
        transferOut.setDirection(MovementDirection.OUT);
        transferOut.setReferenceType("TRANSFER");
        transferOut.setReferenceId(transferReferenceId);
        transferOut.setNotes("Transfer TRF-2026-0002 to Filial Sul");
        transferOut.setCreatedAt(LocalDateTime.now().minusMinutes(12));
        transferOut.setItems(List.of(
                StockMovementItem.builder().productId(UUID.randomUUID()).productName("Produto A").build(),
                StockMovementItem.builder().productId(UUID.randomUUID()).productName("Produto B").build()
        ));

        StockMovement transferIn = new StockMovement();
        transferIn.setId(UUID.randomUUID());
        transferIn.setType(StockMovementType.TRANSFER_IN);
        transferIn.setDirection(MovementDirection.IN);
        transferIn.setReferenceType("TRANSFER");
        transferIn.setReferenceId(transferReferenceId);
        transferIn.setNotes("Transfer TRF-2026-0002 from Depósito Central");
        transferIn.setCreatedAt(LocalDateTime.now().minusMinutes(8));
        transferIn.setItems(List.of(
                StockMovementItem.builder().productId(UUID.randomUUID()).productName("Produto A").build(),
                StockMovementItem.builder().productId(UUID.randomUUID()).productName("Produto B").build()
        ));

        StockMovement purchase = new StockMovement();
        purchase.setId(UUID.randomUUID());
        purchase.setType(StockMovementType.PURCHASE_IN);
        purchase.setDirection(MovementDirection.IN);
        purchase.setNotes("Reposição");
        purchase.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        purchase.setItems(List.of(
                StockMovementItem.builder().productId(UUID.randomUUID()).productName("Produto C").build()
        ));

        when(warehouseAccessService.hasFullAccess()).thenReturn(true);
        when(batchRepository.findAllByTenantId(tenantId)).thenReturn(List.of());
        when(warehouseRepository.findAllByTenantId(tenantId)).thenReturn(List.of(sourceWarehouse, destinationWarehouse));
        when(stockMovementRepository.findWithFilters(
                eq(tenantId),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(PageRequest.of(0, 20))
        )).thenReturn(new PageImpl<>(List.of(purchase, transferIn, transferOut)));
        when(stockMovementRepository.countMovementsByTypeAndPeriod(
                eq(tenantId),
                eq(null),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.<Object[]>of(new Object[]{StockMovementType.PURCHASE_IN, 1L}));
        when(stockMovementRepository.countDistinctTransferReferencesByPeriod(
                eq(tenantId),
                eq(null),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(Set.of(StockMovementType.TRANSFER_IN, StockMovementType.TRANSFER_OUT))
        )).thenReturn(1L);

        DashboardResponse response = reportService.getDashboard();

        assertThat(response.getRecentMovements()).hasSize(2);
        assertThat(response.getRecentMovements().get(0).getMovementType()).isEqualTo("ENTRY");
        assertThat(response.getRecentMovements().get(1).getMovementType()).isEqualTo("TRANSFER");
        assertThat(response.getRecentMovements().get(1).getNotes())
                .isEqualTo("Transfer TRF-2026-0002 to Filial Sul");
        assertThat(response.getRecentMovements().get(1).getProductCount()).isEqualTo(2);
        assertThat(response.getMovementStats().getToday().getTransfers()).isEqualTo(1L);
    }
}
