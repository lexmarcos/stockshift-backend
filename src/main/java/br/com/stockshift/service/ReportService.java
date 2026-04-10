package br.com.stockshift.service;

import br.com.stockshift.dto.report.DashboardAlertsResponse;
import br.com.stockshift.dto.report.DashboardKpisResponse;
import br.com.stockshift.dto.report.DashboardResponse;
import br.com.stockshift.dto.report.DashboardSummaryResponse;
import br.com.stockshift.dto.report.DailyMovement;
import br.com.stockshift.dto.report.KpiPeriodData;
import br.com.stockshift.dto.report.KpiVariations;
import br.com.stockshift.dto.report.MovementTotals;
import br.com.stockshift.dto.report.MovementTrendResponse;
import br.com.stockshift.dto.report.RecentMovementAlert;
import br.com.stockshift.dto.report.StockReportResponse;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.StockMovement;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private static final int DASHBOARD_RECENT_MOVEMENTS_LIMIT = 5;
    private static final int DASHBOARD_RECENT_MOVEMENTS_FETCH_SIZE = 20;
    private static final Set<StockMovementType> TRANSFER_MOVEMENT_TYPES = Set.of(
            StockMovementType.TRANSFER_IN,
            StockMovementType.TRANSFER_OUT
    );

    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final TransferRepository transferRepository;
    private final WarehouseRepository warehouseRepository;
    private final SecurityUtils securityUtils;
    private final WarehouseAccessService warehouseAccessService;
    private final PermissionResolverService permissionResolverService;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        UUID tenantId = TenantContext.getTenantId();
        DashboardWarehouseScope dashboardScope = resolveDashboardWarehouseScope(tenantId);
        List<Batch> allBatches = resolveBatchesForDashboard(tenantId, dashboardScope);
        List<Warehouse> warehouses = dashboardScope.warehouses();

        long totalProducts = allBatches.stream()
                .map(batch -> batch.getProduct().getId())
                .distinct()
                .count();
        long activeProducts = allBatches.stream()
                .map(Batch::getProduct)
                .filter(product -> Boolean.TRUE.equals(product.getActive()))
                .map(br.com.stockshift.model.entity.Product::getId)
                .distinct()
                .count();
        long totalWarehouses = warehouses.size();
        long activeWarehouses = warehouses.stream()
                .filter(warehouse -> Boolean.TRUE.equals(warehouse.getIsActive()))
                .count();
        long totalBatches = allBatches.size();

        BigDecimal totalStockValue = toCurrencyValue(sumStockValue(allBatches));

        long lowStockCount = filterLowStockBatches(allBatches, BigDecimal.TEN).stream()
                .map(batch -> batch.getProduct().getId())
                .distinct()
                .count();
        long expiringCount = filterExpiringBatches(allBatches, 30).stream()
                .map(batch -> batch.getProduct().getId())
                .distinct()
                .count();

        return DashboardResponse.builder()
                .totalProducts(totalProducts)
                .activeProducts(activeProducts)
                .totalWarehouses(totalWarehouses)
                .activeWarehouses(activeWarehouses)
                .totalBatches(totalBatches)
                .totalStockValue(totalStockValue)
                .lowStockCount(lowStockCount)
                .expiringCount(expiringCount)
                .recentMovements(buildRecentMovements(tenantId, dashboardScope))
                .stockByWarehouse(buildStockByWarehouse(allBatches))
                .stockByCategory(buildStockByCategory(allBatches))
                .movementStats(buildMovementStats(tenantId, dashboardScope))
                .build();
    }

    @Transactional(readOnly = true)
    public List<StockReportResponse> getStockReport() {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = resolveCurrentWarehouseId();
        List<Batch> batches;
        if (currentWarehouseId != null) {
            batches = batchRepository.findByWarehouseIdAndTenantId(currentWarehouseId, tenantId);
        } else if (warehouseAccessService.hasFullAccess()) {
            batches = batchRepository.findAllByTenantId(tenantId);
        } else {
            throw new UnauthorizedException("No active warehouse context");
        }

        Map<String, List<Batch>> groupedBatches = batches.stream()
                .collect(Collectors.groupingBy(b ->
                    b.getProduct().getId().toString() + "_" + b.getWarehouse().getId().toString()
                ));

        return groupedBatches.values().stream()
                .map(this::aggregateBatches)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockReportResponse> getLowStockReport(Integer threshold, Integer limit) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = resolveCurrentWarehouseId();
        List<Batch> lowStockBatches = batchRepository.findLowStock(threshold, tenantId);
        if (currentWarehouseId != null) {
            lowStockBatches = lowStockBatches.stream()
                    .filter(batch -> currentWarehouseId.equals(batch.getWarehouse().getId()))
                    .collect(Collectors.toList());
        } else if (!warehouseAccessService.hasFullAccess()) {
            throw new UnauthorizedException("No active warehouse context");
        }

        return lowStockBatches.stream()
                .limit(limit != null ? limit : Long.MAX_VALUE)
                .map(this::batchToReport)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockReportResponse> getExpiringProductsReport(Integer daysAhead, Integer limit) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = resolveCurrentWarehouseId();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);

        List<Batch> expiringBatches = batchRepository.findExpiringBatches(startDate, endDate, tenantId);
        if (currentWarehouseId != null) {
            expiringBatches = expiringBatches.stream()
                    .filter(batch -> currentWarehouseId.equals(batch.getWarehouse().getId()))
                    .collect(Collectors.toList());
        } else if (!warehouseAccessService.hasFullAccess()) {
            throw new UnauthorizedException("No active warehouse context");
        }

        return expiringBatches.stream()
                .limit(limit != null ? limit : Long.MAX_VALUE)
                .map(this::batchToReport)
                .collect(Collectors.toList());
    }

    private StockReportResponse aggregateBatches(List<Batch> batches) {
        Batch first = batches.get(0);

        BigDecimal totalQuantity = batches.stream()
                .map(Batch::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValue = batches.stream()
                .filter(b -> b.getCostPrice() != null)
                .map(b -> BigDecimal.valueOf(b.getCostPrice()).multiply(b.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate nearestExpiration = batches.stream()
                .map(Batch::getExpirationDate)
                .filter(date -> date != null)
                .min(LocalDate::compareTo)
                .orElse(null);

        return StockReportResponse.builder()
                .productId(first.getProduct().getId())
                .productName(first.getProduct().getName())
                .warehouseId(first.getWarehouse().getId())
                .warehouseName(first.getWarehouse().getName())
                .totalQuantity(totalQuantity)
                .totalValue(totalValue)
                .nearestExpiration(nearestExpiration)
                .batchCount(batches.size())
                .build();
    }

    private StockReportResponse batchToReport(Batch batch) {
        BigDecimal totalValue = batch.getCostPrice() != null ?
                BigDecimal.valueOf(batch.getCostPrice()).multiply(batch.getQuantity()) :
                BigDecimal.ZERO;

        return StockReportResponse.builder()
                .productId(batch.getProduct().getId())
                .productName(batch.getProduct().getName())
                .warehouseId(batch.getWarehouse().getId())
                .warehouseName(batch.getWarehouse().getName())
                .totalQuantity(batch.getQuantity())
                .totalValue(totalValue)
                .nearestExpiration(batch.getExpirationDate())
                .batchCount(1)
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        UUID tenantId = TenantContext.getTenantId();
        UUID warehouseId = resolveCurrentWarehouseId();
        resolveOrFail(warehouseId);

        BigDecimal totalStockValue = batchRepository.sumStockValue(tenantId, warehouseId);
        BigDecimal totalTransitQuantity = batchRepository.sumTransitQuantity(tenantId, warehouseId);

        List<Batch> batches;
        if (warehouseId != null) {
            batches = batchRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId);
        } else {
            batches = batchRepository.findAllByTenantId(tenantId);
        }

        long totalProducts = batches.stream()
                .map(b -> b.getProduct().getId())
                .distinct()
                .count();

        long totalWarehouses = warehouseId != null ? 1 :
                warehouseRepository.findAllByTenantId(tenantId).size();

        long totalActiveBatches = batchRepository.countActiveBatches(tenantId, warehouseId);

        BigDecimal totalStockQuantity = batches.stream()
                .map(Batch::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingTransfers = transferRepository.countPendingTransfers(
                tenantId, warehouseId,
                List.of(TransferStatus.DRAFT, TransferStatus.IN_TRANSIT, TransferStatus.PENDING_VALIDATION));

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        long todayMovements = stockMovementRepository.countTodayMovements(tenantId, warehouseId, startOfDay, endOfDay);

        long criticalAlerts = batchRepository.countCriticalAlerts(tenantId, warehouseId,
                BigDecimal.TEN, LocalDate.now().plusDays(7));

        return DashboardSummaryResponse.builder()
                .totalProducts(totalProducts)
                .totalWarehouses(totalWarehouses)
                .totalActiveBatches(totalActiveBatches)
                .totalStockQuantity(totalStockQuantity)
                .totalStockValue(totalStockValue)
                .totalTransitQuantity(totalTransitQuantity)
                .pendingTransfers(pendingTransfers)
                .todayMovements(todayMovements)
                .criticalAlerts(criticalAlerts)
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardKpisResponse getKpis() {
        UUID tenantId = TenantContext.getTenantId();
        UUID warehouseId = resolveCurrentWarehouseId();
        resolveOrFail(warehouseId);

        LocalDate today = LocalDate.now();
        LocalDateTime currentMonthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime currentMonthEnd = today.plusDays(1).atStartOfDay();
        LocalDateTime previousMonthStart = currentMonthStart.minusMonths(1);
        LocalDateTime previousMonthEnd = currentMonthStart;

        KpiPeriodData currentMonthData = buildKpiPeriodData(tenantId, warehouseId, currentMonthStart, currentMonthEnd);
        KpiPeriodData previousMonthData = buildKpiPeriodData(tenantId, warehouseId, previousMonthStart, previousMonthEnd);

        KpiVariations variations = null;
        if (previousMonthData != null) {
            variations = KpiVariations.builder()
                    .totalStockValue(calcVariation(currentMonthData.getTotalStockValue(), previousMonthData.getTotalStockValue()))
                    .totalPurchasesValue(calcVariation(currentMonthData.getTotalPurchasesValue(), previousMonthData.getTotalPurchasesValue()))
                    .totalLossesValue(calcVariation(currentMonthData.getTotalLossesValue(), previousMonthData.getTotalLossesValue()))
                    .totalDamageValue(calcVariation(currentMonthData.getTotalDamageValue(), previousMonthData.getTotalDamageValue()))
                    .totalGiftValue(calcVariation(currentMonthData.getTotalGiftValue(), previousMonthData.getTotalGiftValue()))
                    .totalAdjustmentValue(calcVariation(currentMonthData.getTotalAdjustmentValue(), previousMonthData.getTotalAdjustmentValue()))
                    .totalTransitValue(calcVariation(currentMonthData.getTotalTransitValue(), previousMonthData.getTotalTransitValue()))
                    .stockTurnoverRate(calcVariation(currentMonthData.getStockTurnoverRate(), previousMonthData.getStockTurnoverRate()))
                    .build();
        }

        return DashboardKpisResponse.builder()
                .currentMonth(currentMonthData)
                .previousMonth(previousMonthData)
                .variations(variations)
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardAlertsResponse getAlerts() {
        UUID tenantId = TenantContext.getTenantId();
        UUID warehouseId = resolveCurrentWarehouseId();
        resolveOrFail(warehouseId);

        List<StockReportResponse> lowStockProducts = getLowStockReport(10, 10);
        List<StockReportResponse> expiringProducts = getExpiringProductsReport(30, 10);

        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<Object[]> lossRows = stockMovementRepository.findRecentLosses(
                tenantId, warehouseId,
                List.of(StockMovementType.LOSS, StockMovementType.DAMAGE), since);

        List<RecentMovementAlert> recentLosses = lossRows.stream()
                .limit(10)
                .map(row -> {
                    UUID batchId = (UUID) row[4];
                    BigDecimal lossValue = BigDecimal.ZERO;
                    if (batchId != null) {
                        Batch batch = batchRepository.findById(batchId).orElse(null);
                        if (batch != null && batch.getCostPrice() != null) {
                            lossValue = BigDecimal.valueOf(batch.getCostPrice()).multiply((BigDecimal) row[2]);
                        }
                    }
                    return RecentMovementAlert.builder()
                            .movementType((StockMovementType) row[0])
                            .productName((String) row[1])
                            .quantity((BigDecimal) row[2])
                            .value(lossValue)
                            .date(((java.time.Instant) row[3]).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                            .build();
                })
                .collect(Collectors.toList());

        long pendingTransfers = transferRepository.countPendingTransfers(
                tenantId, warehouseId,
                List.of(TransferStatus.DRAFT, TransferStatus.IN_TRANSIT, TransferStatus.PENDING_VALIDATION));

        List<Batch> allBatches;
        if (warehouseId != null) {
            allBatches = batchRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId);
        } else {
            allBatches = batchRepository.findAllByTenantId(tenantId);
        }
        BigDecimal highTransitValue = allBatches.stream()
                .filter(b -> b.getTransitQuantity() != null && b.getTransitQuantity().compareTo(BigDecimal.ZERO) > 0)
                .filter(b -> b.getCostPrice() != null)
                .map(b -> BigDecimal.valueOf(b.getCostPrice()).multiply(b.getTransitQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DashboardAlertsResponse.builder()
                .lowStockProducts(lowStockProducts)
                .expiringProducts(expiringProducts)
                .recentLosses(recentLosses)
                .pendingTransfers(pendingTransfers)
                .highTransitValue(highTransitValue)
                .build();
    }

    @Transactional(readOnly = true)
    public MovementTrendResponse getMovementTrend(Integer days) {
        UUID tenantId = TenantContext.getTenantId();
        UUID warehouseId = resolveCurrentWarehouseId();
        resolveOrFail(warehouseId);

        int periodDays = days != null ? days : 30;
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(periodDays - 1);

        List<Object[]> trendRows = stockMovementRepository.getDailyMovementTrend(
                tenantId, warehouseId,
                startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());

        java.util.Map<LocalDate, DailyMovement> dailyMap = new java.util.LinkedHashMap<>();
        for (int i = 0; i < periodDays; i++) {
            LocalDate date = startDate.plusDays(i);
            dailyMap.put(date, DailyMovement.builder()
                    .date(date)
                    .totalInQuantity(BigDecimal.ZERO)
                    .totalInValue(BigDecimal.ZERO)
                    .totalOutQuantity(BigDecimal.ZERO)
                    .totalOutValue(BigDecimal.ZERO)
                    .movementCount(0L)
                    .build());
        }

        for (Object[] row : trendRows) {
            LocalDate date = (LocalDate) row[0];
            MovementDirection direction = (MovementDirection) row[1];
            BigDecimal quantity = (BigDecimal) row[2];
            Long count = ((Number) row[3]).longValue();

            DailyMovement existing = dailyMap.get(date);
            if (existing != null) {
                if (direction == MovementDirection.IN) {
                    dailyMap.put(date, DailyMovement.builder()
                            .date(date)
                            .totalInQuantity(existing.getTotalInQuantity().add(quantity))
                            .totalInValue(existing.getTotalInValue())
                            .totalOutQuantity(existing.getTotalOutQuantity())
                            .totalOutValue(existing.getTotalOutValue())
                            .movementCount(existing.getMovementCount() + count)
                            .build());
                } else {
                    dailyMap.put(date, DailyMovement.builder()
                            .date(date)
                            .totalInQuantity(existing.getTotalInQuantity())
                            .totalInValue(existing.getTotalInValue())
                            .totalOutQuantity(existing.getTotalOutQuantity().add(quantity))
                            .totalOutValue(existing.getTotalOutValue())
                            .movementCount(existing.getMovementCount() + count)
                            .build());
                }
            }
        }

        List<DailyMovement> dailyMovements = new java.util.ArrayList<>(dailyMap.values());

        MovementTotals totals = MovementTotals.builder()
                .totalInQuantity(dailyMovements.stream().map(DailyMovement::getTotalInQuantity).reduce(BigDecimal.ZERO, BigDecimal::add))
                .totalInValue(dailyMovements.stream().map(DailyMovement::getTotalInValue).reduce(BigDecimal.ZERO, BigDecimal::add))
                .totalOutQuantity(dailyMovements.stream().map(DailyMovement::getTotalOutQuantity).reduce(BigDecimal.ZERO, BigDecimal::add))
                .totalOutValue(dailyMovements.stream().map(DailyMovement::getTotalOutValue).reduce(BigDecimal.ZERO, BigDecimal::add))
                .movementCount(dailyMovements.stream().mapToLong(DailyMovement::getMovementCount).sum())
                .build();

        return MovementTrendResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .days(dailyMovements)
                .totals(totals)
                .build();
    }

    private KpiPeriodData buildKpiPeriodData(UUID tenantId, UUID warehouseId,
                                              LocalDateTime start, LocalDateTime end) {
        List<Object[]> typeRows = stockMovementRepository.sumMovementsByTypeAndPeriod(tenantId, warehouseId, start, end);

        BigDecimal purchasesValue = BigDecimal.ZERO;
        BigDecimal lossesValue = BigDecimal.ZERO;
        BigDecimal damageValue = BigDecimal.ZERO;
        BigDecimal giftValue = BigDecimal.ZERO;
        BigDecimal adjustmentValue = BigDecimal.ZERO;
        BigDecimal totalOutQuantity = BigDecimal.ZERO;

        for (Object[] row : typeRows) {
            StockMovementType type = (StockMovementType) row[0];
            BigDecimal quantity = (BigDecimal) row[2];
            if (type == StockMovementType.PURCHASE_IN) purchasesValue = quantity;
            if (type == StockMovementType.LOSS) lossesValue = quantity;
            if (type == StockMovementType.DAMAGE) damageValue = quantity;
            if (type == StockMovementType.GIFT) giftValue = quantity;
            if (type == StockMovementType.ADJUSTMENT_IN || type == StockMovementType.ADJUSTMENT_OUT) {
                adjustmentValue = adjustmentValue.add(quantity);
            }
            if (type.isDebit()) {
                totalOutQuantity = totalOutQuantity.add(quantity);
            }
        }

        BigDecimal currentStockValue = batchRepository.sumStockValue(tenantId, warehouseId);
        BigDecimal transitValue = batchRepository.sumTransitQuantity(tenantId, warehouseId);

        BigDecimal turnoverRate = BigDecimal.ZERO;
        if (currentStockValue != null && currentStockValue.compareTo(BigDecimal.ZERO) > 0) {
            turnoverRate = totalOutQuantity.divide(currentStockValue, 2, RoundingMode.HALF_UP);
        }

        return KpiPeriodData.builder()
                .totalStockValue(currentStockValue)
                .totalPurchasesValue(purchasesValue)
                .totalLossesValue(lossesValue)
                .totalDamageValue(damageValue)
                .totalGiftValue(giftValue)
                .totalAdjustmentValue(adjustmentValue)
                .totalTransitValue(transitValue)
                .stockTurnoverRate(turnoverRate)
                .build();
    }

    private BigDecimal calcVariation(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private void resolveOrFail(UUID warehouseId) {
        if (warehouseId == null && !warehouseAccessService.hasFullAccess()) {
            throw new UnauthorizedException("No active warehouse context");
        }
    }

    private record DashboardWarehouseScope(
            List<Warehouse> warehouses,
            List<UUID> warehouseIds,
            boolean fullTenantAccess
    ) {}

    private DashboardWarehouseScope resolveDashboardWarehouseScope(UUID tenantId) {
        List<Warehouse> tenantWarehouses = warehouseRepository.findAllByTenantId(tenantId);
        if (warehouseAccessService.hasFullAccess()) {
            return new DashboardWarehouseScope(
                    tenantWarehouses,
                    tenantWarehouses.stream().map(Warehouse::getId).toList(),
                    true
            );
        }

        UUID userId = securityUtils.getCurrentUserId();
        List<Warehouse> accessibleWarehouses = tenantWarehouses.stream()
                .filter(warehouse -> hasDashboardWarehouseAccess(userId, warehouse.getId()))
                .toList();

        if (accessibleWarehouses.isEmpty()) {
            throw new UnauthorizedException("No active warehouse context");
        }

        return new DashboardWarehouseScope(
                accessibleWarehouses,
                accessibleWarehouses.stream().map(Warehouse::getId).toList(),
                false
        );
    }

    private boolean hasDashboardWarehouseAccess(UUID userId, UUID warehouseId) {
        return permissionResolverService.resolveUserPermissions(userId, warehouseId).stream()
                .anyMatch(permission -> "*".equals(permission) || PermissionCodes.REPORTS_READ.equals(permission));
    }

    private List<Batch> resolveBatchesForDashboard(UUID tenantId, DashboardWarehouseScope dashboardScope) {
        if (dashboardScope.fullTenantAccess()) {
            return batchRepository.findAllByTenantId(tenantId);
        }
        return batchRepository.findByTenantIdAndWarehouseIdIn(tenantId, dashboardScope.warehouseIds());
    }

    private BigDecimal sumStockValue(List<Batch> batches) {
        return batches.stream()
                .filter(batch -> batch.getCostPrice() != null)
                .map(batch -> BigDecimal.valueOf(batch.getCostPrice()).multiply(batch.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal toCurrencyValue(BigDecimal centsValue) {
        return centsValue.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private List<Batch> filterLowStockBatches(List<Batch> batches, BigDecimal threshold) {
        return batches.stream()
                .filter(batch -> batch.getQuantity() != null && batch.getQuantity().compareTo(threshold) <= 0)
                .collect(Collectors.toList());
    }

    private List<Batch> filterExpiringBatches(List<Batch> batches, int daysAhead) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(daysAhead);

        return batches.stream()
                .filter(batch -> batch.getExpirationDate() != null)
                .filter(batch -> !batch.getExpirationDate().isBefore(today) && !batch.getExpirationDate().isAfter(endDate))
                .collect(Collectors.toList());
    }

    private List<DashboardResponse.RecentMovement> buildRecentMovements(
            UUID tenantId,
            DashboardWarehouseScope dashboardScope
    ) {
        var recentMovements = dashboardScope.fullTenantAccess()
                ? stockMovementRepository.findWithFilters(
                        tenantId,
                        null,
                        null,
                        null,
                        null,
                        PageRequest.of(0, DASHBOARD_RECENT_MOVEMENTS_FETCH_SIZE)
                )
                : stockMovementRepository.findWithFiltersByWarehouseIds(
                        tenantId,
                        dashboardScope.warehouseIds(),
                        null,
                        null,
                        null,
                        PageRequest.of(0, DASHBOARD_RECENT_MOVEMENTS_FETCH_SIZE)
                );

        Map<UUID, List<StockMovement>> groupedTransferMovements = new LinkedHashMap<>();
        List<DashboardResponse.RecentMovement> mappedMovements = new ArrayList<>();

        for (StockMovement movement : recentMovements) {
            if (isTransferMovement(movement)) {
                groupedTransferMovements
                        .computeIfAbsent(movement.getReferenceId(), ignored -> new ArrayList<>())
                        .add(movement);
                continue;
            }

            mappedMovements.add(toRecentMovement(movement, movement.getCreatedAt()));
        }

        groupedTransferMovements.values().stream()
                .map(this::toTransferRecentMovement)
                .forEach(mappedMovements::add);

        return mappedMovements
                .stream()
                .sorted(Comparator.comparing(DashboardResponse.RecentMovement::getCreatedAt).reversed())
                .limit(DASHBOARD_RECENT_MOVEMENTS_LIMIT)
                .toList();
    }

    private List<DashboardResponse.StockByWarehouse> buildStockByWarehouse(List<Batch> batches) {
        return batches.stream()
                .collect(Collectors.groupingBy(Batch::getWarehouse))
                .entrySet()
                .stream()
                .map(entry -> DashboardResponse.StockByWarehouse.builder()
                        .warehouseId(entry.getKey().getId())
                        .warehouseName(entry.getKey().getName())
                        .batchCount((long) entry.getValue().size())
                        .stockValue(toCurrencyValue(sumStockValue(entry.getValue())))
                        .productCount(entry.getValue().stream()
                                .map(batch -> batch.getProduct().getId())
                                .distinct()
                                .count())
                        .build())
                .sorted(Comparator.comparing(DashboardResponse.StockByWarehouse::getStockValue).reversed())
                .collect(Collectors.toList());
    }

    private List<DashboardResponse.StockByCategory> buildStockByCategory(List<Batch> batches) {
        record CategoryKey(String id, String name) {}

        return batches.stream()
                .collect(Collectors.groupingBy(batch -> {
                    if (batch.getProduct().getCategory() == null) {
                        return new CategoryKey("uncategorized", "Sem categoria");
                    }
                    return new CategoryKey(
                            batch.getProduct().getCategory().getId().toString(),
                            batch.getProduct().getCategory().getName()
                    );
                }))
                .entrySet()
                .stream()
                .map(entry -> DashboardResponse.StockByCategory.builder()
                        .categoryId(entry.getKey().id())
                        .categoryName(entry.getKey().name())
                        .batchCount((long) entry.getValue().size())
                        .stockValue(toCurrencyValue(sumStockValue(entry.getValue())))
                        .productCount(entry.getValue().stream()
                                .map(batch -> batch.getProduct().getId())
                                .distinct()
                                .count())
                        .build())
                .sorted(Comparator.comparing(DashboardResponse.StockByCategory::getStockValue).reversed())
                .collect(Collectors.toList());
    }

    private DashboardResponse.MovementStats buildMovementStats(
            UUID tenantId,
            DashboardWarehouseScope dashboardScope
    ) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();
        LocalDateTime startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();

        return DashboardResponse.MovementStats.builder()
                .today(buildMovementStatsPeriod(tenantId, dashboardScope, startOfToday, startOfTomorrow))
                .thisWeek(buildMovementStatsPeriod(tenantId, dashboardScope, startOfWeek, startOfTomorrow))
                .thisMonth(buildMovementStatsPeriod(tenantId, dashboardScope, startOfMonth, startOfTomorrow))
                .build();
    }

    private DashboardResponse.MovementStatsPeriod buildMovementStatsPeriod(
            UUID tenantId,
            DashboardWarehouseScope dashboardScope,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        long entries = 0L;
        long exits = 0L;
        long adjustments = 0L;
        long transfers = countTransferEvents(tenantId, dashboardScope, startDate, endDate);

        List<Object[]> rows = dashboardScope.fullTenantAccess()
                ? stockMovementRepository.countMovementsByTypeAndPeriod(tenantId, null, startDate, endDate)
                : stockMovementRepository.countMovementsByTypeAndPeriodByWarehouseIds(
                        tenantId,
                        dashboardScope.warehouseIds(),
                        startDate,
                        endDate
                );

        for (Object[] row : rows) {
            StockMovementType type = (StockMovementType) row[0];
            long count = ((Number) row[1]).longValue();

            switch (type) {
                case PURCHASE_IN -> entries += count;
                case ADJUSTMENT_IN, ADJUSTMENT_OUT -> adjustments += count;
                case USAGE, GIFT, LOSS, DAMAGE -> exits += count;
                case TRANSFER_IN, TRANSFER_OUT -> {
                    // Transfer stats are counted by distinct transfer reference to avoid double-counting IN/OUT legs.
                }
            }
        }

        return DashboardResponse.MovementStatsPeriod.builder()
                .entries(entries)
                .exits(exits)
                .transfers(transfers)
                .adjustments(adjustments)
                .build();
    }

    private long countTransferEvents(
            UUID tenantId,
            DashboardWarehouseScope dashboardScope,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        return dashboardScope.fullTenantAccess()
                ? stockMovementRepository.countDistinctTransferReferencesByPeriod(
                        tenantId,
                        null,
                        startDate,
                        endDate,
                        TRANSFER_MOVEMENT_TYPES
                )
                : stockMovementRepository.countDistinctTransferReferencesByPeriodByWarehouseIds(
                        tenantId,
                        dashboardScope.warehouseIds(),
                        startDate,
                        endDate,
                        TRANSFER_MOVEMENT_TYPES
                );
    }

    private boolean isTransferMovement(StockMovement movement) {
        return movement.getReferenceId() != null
                && "TRANSFER".equals(movement.getReferenceType())
                && TRANSFER_MOVEMENT_TYPES.contains(movement.getType());
    }

    private DashboardResponse.RecentMovement toTransferRecentMovement(List<StockMovement> transferMovements) {
        StockMovement representativeMovement = transferMovements.stream()
                .filter(movement -> movement.getType() == StockMovementType.TRANSFER_OUT)
                .findFirst()
                .orElseGet(() -> transferMovements.stream()
                        .max(Comparator.comparing(StockMovement::getCreatedAt))
                        .orElseThrow());

        LocalDateTime latestTransferTimestamp = transferMovements.stream()
                .map(StockMovement::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(representativeMovement.getCreatedAt());

        return toRecentMovement(representativeMovement, latestTransferTimestamp);
    }

    private DashboardResponse.RecentMovement toRecentMovement(
            StockMovement movement,
            LocalDateTime createdAt
    ) {
        return DashboardResponse.RecentMovement.builder()
                .id(movement.getId())
                .movementType(mapMovementType(movement.getType()))
                .status("COMPLETED")
                .createdAt(createdAt)
                .productCount(movement.getItems() != null ? movement.getItems().size() : 0)
                .notes(movement.getNotes())
                .build();
    }

    private String mapMovementType(StockMovementType type) {
        return switch (type) {
            case PURCHASE_IN -> "ENTRY";
            case TRANSFER_IN, TRANSFER_OUT -> "TRANSFER";
            case ADJUSTMENT_IN, ADJUSTMENT_OUT -> "ADJUSTMENT";
            case USAGE, GIFT, LOSS, DAMAGE -> "EXIT";
        };
    }

    private UUID resolveCurrentWarehouseId() {
        try {
            return securityUtils.getCurrentWarehouseId();
        } catch (UnauthorizedException ex) {
            return null;
        }
    }
}
