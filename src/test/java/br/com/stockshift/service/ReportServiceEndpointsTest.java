package br.com.stockshift.service;

import br.com.stockshift.dto.report.DashboardAlertsResponse;
import br.com.stockshift.dto.report.DashboardKpisResponse;
import br.com.stockshift.dto.report.DashboardResponse;
import br.com.stockshift.dto.report.DashboardSummaryResponse;
import br.com.stockshift.dto.report.MovementTrendResponse;
import br.com.stockshift.dto.report.StockReportResponse;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.entity.StockMovementItem;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.StockMovementRepository;
import br.com.stockshift.repository.TransferRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.PermissionCodes;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceEndpointsTest {

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
    private Warehouse warehouse;
    private Product product;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        warehouse = warehouse(warehouseId, "Principal");
        product = product("Produto");
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void stockReportsShouldUseWarehouseContextAndAggregateBatches() {
        Batch first = batch(product, warehouse, "7", 200L, LocalDate.now().plusDays(10));
        Batch second = batch(product, warehouse, "3", 100L, LocalDate.now().plusDays(3));
        Batch otherWarehouse = batch(product, warehouse(UUID.randomUUID(), "Filial"), "1", 100L, null);
        when(securityUtils.getCurrentWarehouseId()).thenReturn(warehouseId);
        when(batchRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId))
                .thenReturn(List.of(first, second));
        when(batchRepository.findLowStock(10, tenantId))
                .thenReturn(List.of(first, otherWarehouse));
        when(batchRepository.findExpiringBatches(any(LocalDate.class), any(LocalDate.class), eq(tenantId)))
                .thenReturn(List.of(second, otherWarehouse));

        List<StockReportResponse> stock = reportService.getStockReport();
        List<StockReportResponse> lowStock = reportService.getLowStockReport(10, 1);
        List<StockReportResponse> expiring = reportService.getExpiringProductsReport(30, 5);

        assertThat(stock).singleElement().satisfies(item -> {
            assertThat(item.getProductId()).isEqualTo(product.getId());
            assertThat(item.getWarehouseId()).isEqualTo(warehouseId);
            assertThat(item.getTotalQuantity()).isEqualByComparingTo("10");
            assertThat(item.getTotalValue()).isEqualByComparingTo("1700");
            assertThat(item.getNearestExpiration()).isEqualTo(second.getExpirationDate());
            assertThat(item.getBatchCount()).isEqualTo(2);
        });
        assertThat(lowStock).singleElement().extracting("warehouseId").isEqualTo(warehouseId);
        assertThat(expiring).singleElement().extracting("nearestExpiration")
                .isEqualTo(second.getExpirationDate());
    }

    @Test
    void summaryKpisAndMovementTrendShouldMapRepositoryTotals() {
        when(securityUtils.getCurrentWarehouseId()).thenReturn(warehouseId);
        when(batchRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId))
                .thenReturn(List.of(batch(product, warehouse, "4", 500L, null)));
        when(batchRepository.sumStockValue(tenantId, warehouseId))
                .thenReturn(new BigDecimal("2000"), new BigDecimal("100"), new BigDecimal("50"));
        when(batchRepository.sumTransitQuantity(tenantId, warehouseId))
                .thenReturn(new BigDecimal("6"), new BigDecimal("8"), new BigDecimal("4"));
        when(batchRepository.countActiveBatches(tenantId, warehouseId)).thenReturn(3L);
        when(batchRepository.countCriticalAlerts(eq(tenantId), eq(warehouseId), any(BigDecimal.class), any(LocalDate.class)))
                .thenReturn(2L);
        when(transferRepository.countPendingTransfers(eq(tenantId), eq(warehouseId), any()))
                .thenReturn(5L);
        when(stockMovementRepository.countTodayMovements(eq(tenantId), eq(warehouseId), any(), any()))
                .thenReturn(9L);
        when(stockMovementRepository.sumMovementsByTypeAndPeriod(eq(tenantId), eq(warehouseId), any(), any()))
                .thenReturn(
                        List.<Object[]>of(
                                movementSum(StockMovementType.PURCHASE_IN, MovementDirection.IN, "20"),
                                movementSum(StockMovementType.LOSS, MovementDirection.OUT, "3"),
                                movementSum(StockMovementType.DAMAGE, MovementDirection.OUT, "2"),
                                movementSum(StockMovementType.GIFT, MovementDirection.OUT, "1"),
                                movementSum(StockMovementType.ADJUSTMENT_IN, MovementDirection.IN, "4"),
                                movementSum(StockMovementType.ADJUSTMENT_OUT, MovementDirection.OUT, "1"),
                                movementSum(StockMovementType.SALE, MovementDirection.OUT, "3")
                        ),
                        List.<Object[]>of(movementSum(StockMovementType.PURCHASE_IN, MovementDirection.IN, "10"))
                );
        LocalDate today = LocalDate.now();
        when(stockMovementRepository.getDailyMovementTrend(eq(tenantId), eq(warehouseId), any(), any()))
                .thenReturn(List.of(
                        new Object[]{today.minusDays(1), MovementDirection.IN, new BigDecimal("4"), 2L},
                        new Object[]{today, MovementDirection.OUT, new BigDecimal("3"), 1L}
                ));

        DashboardSummaryResponse summary = reportService.getSummary();
        DashboardKpisResponse kpis = reportService.getKpis();
        MovementTrendResponse trend = reportService.getMovementTrend(2);

        assertThat(summary.getTotalStockValue()).isEqualByComparingTo("2000");
        assertThat(summary.getPendingTransfers()).isEqualTo(5L);
        assertThat(summary.getTodayMovements()).isEqualTo(9L);
        assertThat(kpis.getCurrentMonth().getTotalPurchasesValue()).isEqualByComparingTo("20");
        assertThat(kpis.getCurrentMonth().getTotalAdjustmentValue()).isEqualByComparingTo("5");
        assertThat(kpis.getVariations().getTotalStockValue()).isEqualByComparingTo("100.00");
        assertThat(trend.getDays()).hasSize(2);
        assertThat(trend.getTotals().getTotalInQuantity()).isEqualByComparingTo("4");
        assertThat(trend.getTotals().getTotalOutQuantity()).isEqualByComparingTo("3");
        assertThat(trend.getTotals().getMovementCount()).isEqualTo(3L);
    }

    @Test
    void alertsShouldIncludeLowStockExpiringLossesPendingTransfersAndTransitValue() {
        Batch lowStock = batch(product, warehouse, "2", 300L, null);
        Batch expiring = batch(product, warehouse, "5", 400L, LocalDate.now().plusDays(2));
        Batch transit = batch(product, warehouse, "8", 1000L, null);
        transit.setTransitQuantity(new BigDecimal("6"));
        UUID lossBatchId = UUID.randomUUID();
        Batch lossBatch = batch(product, warehouse, "4", 250L, null);
        lossBatch.setId(lossBatchId);
        when(securityUtils.getCurrentWarehouseId()).thenReturn(warehouseId);
        when(batchRepository.findLowStock(10, tenantId)).thenReturn(List.of(lowStock));
        when(batchRepository.findExpiringBatches(any(LocalDate.class), any(LocalDate.class), eq(tenantId)))
                .thenReturn(List.of(expiring));
        when(stockMovementRepository.findRecentLosses(eq(tenantId), eq(warehouseId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{
                        StockMovementType.LOSS,
                        product.getName(),
                        new BigDecimal("3"),
                        Instant.now(),
                        lossBatchId
                }));
        when(batchRepository.findById(lossBatchId)).thenReturn(java.util.Optional.of(lossBatch));
        when(transferRepository.countPendingTransfers(eq(tenantId), eq(warehouseId), any()))
                .thenReturn(4L);
        when(batchRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId))
                .thenReturn(List.of(transit));

        DashboardAlertsResponse alerts = reportService.getAlerts();

        assertThat(alerts.getLowStockProducts()).singleElement().extracting("totalQuantity")
                .isEqualTo(new BigDecimal("2"));
        assertThat(alerts.getExpiringProducts()).singleElement().extracting("nearestExpiration")
                .isEqualTo(expiring.getExpirationDate());
        assertThat(alerts.getRecentLosses()).singleElement().satisfies(loss -> {
            assertThat(loss.getProductName()).isEqualTo(product.getName());
            assertThat(loss.getValue()).isEqualByComparingTo("750");
        });
        assertThat(alerts.getPendingTransfers()).isEqualTo(4L);
        assertThat(alerts.getHighTransitValue()).isEqualByComparingTo("6000");
    }

    @Test
    void dashboardShouldUseAccessibleWarehouseScopeWhenUserHasNoFullAccess() {
        UUID userId = UUID.randomUUID();
        Warehouse accessible = warehouse(warehouseId, "Principal");
        Warehouse inaccessible = warehouse(UUID.randomUUID(), "Restrito");
        Batch scopedBatch = batch(product, accessible, "12", 100L, null);
        StockMovement movement = movement(StockMovementType.ADJUSTMENT_OUT, MovementDirection.OUT);
        when(warehouseAccessService.hasFullAccess()).thenReturn(false);
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(warehouseRepository.findAllByTenantId(tenantId)).thenReturn(List.of(accessible, inaccessible));
        when(permissionResolverService.resolveUserPermissions(userId, accessible.getId()))
                .thenReturn(Set.of(PermissionCodes.REPORTS_READ));
        when(permissionResolverService.resolveUserPermissions(userId, inaccessible.getId()))
                .thenReturn(Set.of());
        when(batchRepository.findByTenantIdAndWarehouseIdIn(tenantId, List.of(accessible.getId())))
                .thenReturn(List.of(scopedBatch));
        when(stockMovementRepository.findWithFiltersByWarehouseIds(
                eq(tenantId),
                eq(List.of(accessible.getId())),
                eq(null),
                eq(null),
                eq(null),
                eq(PageRequest.of(0, 20))
        )).thenReturn(new PageImpl<>(List.of(movement)));
        when(stockMovementRepository.countMovementsByTypeAndPeriodByWarehouseIds(
                eq(tenantId),
                eq(List.of(accessible.getId())),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.<Object[]>of(new Object[]{StockMovementType.USAGE, 2L}));
        when(stockMovementRepository.countDistinctTransferReferencesByPeriodByWarehouseIds(
                eq(tenantId),
                eq(List.of(accessible.getId())),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(Set.of(StockMovementType.TRANSFER_IN, StockMovementType.TRANSFER_OUT))
        )).thenReturn(1L);

        DashboardResponse dashboard = reportService.getDashboard();

        assertThat(dashboard.getTotalWarehouses()).isEqualTo(1L);
        assertThat(dashboard.getStockByCategory()).singleElement().satisfies(category -> {
            assertThat(category.getCategoryId()).isEqualTo("uncategorized");
            assertThat(category.getCategoryName()).isEqualTo("Sem categoria");
        });
        assertThat(dashboard.getMovementStats().getToday().getExits()).isEqualTo(2L);
        assertThat(dashboard.getMovementStats().getToday().getTransfers()).isEqualTo(1L);
    }

    @Test
    void reportEndpointsShouldRejectMissingWarehouseScopeWithoutFullAccess() {
        when(securityUtils.getCurrentWarehouseId()).thenThrow(new UnauthorizedException("no warehouse"));
        when(warehouseAccessService.hasFullAccess()).thenReturn(false);

        assertThatThrownBy(() -> reportService.getStockReport())
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("No active warehouse context");
        assertThatThrownBy(() -> reportService.getMovementTrend(null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("No active warehouse context");
    }

    private Object[] movementSum(
            StockMovementType type,
            MovementDirection direction,
            String quantity
    ) {
        return new Object[]{type, direction, new BigDecimal(quantity)};
    }

    private StockMovement movement(StockMovementType type, MovementDirection direction) {
        StockMovement movement = new StockMovement();
        movement.setId(UUID.randomUUID());
        movement.setType(type);
        movement.setDirection(direction);
        movement.setCreatedAt(LocalDateTime.now());
        movement.setItems(List.of(StockMovementItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .build()));
        return movement;
    }

    private Batch batch(
            Product product,
            Warehouse warehouse,
            String quantity,
            Long costPrice,
            LocalDate expirationDate
    ) {
        Batch batch = new Batch();
        batch.setId(UUID.randomUUID());
        batch.setTenantId(tenantId);
        batch.setProduct(product);
        batch.setWarehouse(warehouse);
        batch.setQuantity(new BigDecimal(quantity));
        batch.setCostPrice(costPrice);
        batch.setExpirationDate(expirationDate);
        batch.setTransitQuantity(BigDecimal.ZERO);
        return batch;
    }

    private Product product(String name) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setTenantId(tenantId);
        product.setName(name);
        product.setActive(true);
        return product;
    }

    private Warehouse warehouse(UUID id, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setTenantId(tenantId);
        warehouse.setName(name);
        warehouse.setIsActive(true);
        return warehouse;
    }
}
