# Dashboard KPIs - Design Spec

**Date:** 2026-04-08  
**Status:** Approved  
**Approach:** JPQL queries in existing repositories (Approach A)

## Context

The current dashboard endpoint (`GET /api/reports/dashboard`) returns basic totals (products, warehouses, stock quantity/value) plus low-stock and expiring products lists. It aggregates data in-memory via the `ReportService`. This is insufficient for a manager/owner profile that needs financial visibility and operational health indicators.

The goal is to transform the dashboard into a comprehensive KPI panel with financial metrics, period comparison, movement trends, and operational alerts — split into separate endpoints for performance and caching.

## Entities Available

- `StockMovement` (with direction IN/OUT and types: USAGE, GIFT, LOSS, DAMAGE, PURCHASE_IN, ADJUSTMENT_IN, ADJUSTMENT_OUT, TRANSFER_IN, TRANSFER_OUT)
- `StockMovementItem` (quantity per product/batch in each movement)
- `Transfer` (with status flow: DRAFT -> VALIDATED -> EXECUTED/CANCELLED)
- `Batch` (with costPrice, sellingPrice, transitQuantity, expirationDate)
- `Product`, `Warehouse`, `Category`, `Brand`

## Endpoint Structure

### 1. `GET /api/reports/dashboard/summary`

Quick-load summary for instant dashboard rendering.

**Response:** `ApiResponse<DashboardSummaryResponse>`

```java
public class DashboardSummaryResponse {
    private Long totalProducts;
    private Long totalWarehouses;
    private Long totalActiveBatches;
    private BigDecimal totalStockQuantity;
    private BigDecimal totalStockValue;
    private BigDecimal totalTransitQuantity;
    private Long pendingTransfers;
    private Long todayMovements;
    private Long criticalAlerts;
}
```

- `criticalAlerts` = count of products with stock below threshold (10) + products expiring within 7 days
- `todayMovements` = count of stock movements created today
- All values respect tenant + warehouse context

### 2. `GET /api/reports/dashboard/kpis`

Financial KPIs with month-over-month comparison.

**Response:** `ApiResponse<DashboardKpisResponse>`

```java
public class DashboardKpisResponse {
    private KpiPeriodData currentMonth;
    private KpiPeriodData previousMonth;
    private KpiVariations variations;
}

public class KpiPeriodData {
    private BigDecimal totalStockValue;
    private BigDecimal totalPurchasesValue;
    private BigDecimal totalLossesValue;
    private BigDecimal totalDamageValue;
    private BigDecimal totalGiftValue;
    private BigDecimal totalAdjustmentValue;
    private BigDecimal totalTransitValue;
    private BigDecimal stockTurnoverRate;
}

public class KpiVariations {
    private BigDecimal totalStockValue;      // % variation
    private BigDecimal totalPurchasesValue;
    private BigDecimal totalLossesValue;
    private BigDecimal totalDamageValue;
    private BigDecimal totalGiftValue;
    private BigDecimal totalAdjustmentValue;
    private BigDecimal totalTransitValue;
    private BigDecimal stockTurnoverRate;
}
```

- Periods are calendar months based on `StockMovement.createdAt`
- `previousMonth` can be `null` if no historical data exists
- Variation formula: `((current - previous) / previous) * 100`
- `stockTurnoverRate` = total OUT movement value / average stock value in period
- Movement values come from `StockMovementItem.quantity * Batch.costPrice`

### 3. `GET /api/reports/dashboard/alerts`

Operational alerts and recent issues.

**Response:** `ApiResponse<DashboardAlertsResponse>`

```java
public class DashboardAlertsResponse {
    private List<StockReportResponse> lowStockProducts;    // top 10, threshold 10
    private List<StockReportResponse> expiringProducts;    // top 10, next 30 days
    private List<RecentMovementAlert> recentLosses;        // last 30 days
    private Long pendingTransfers;
    private BigDecimal highTransitValue;
}

public class RecentMovementAlert {
    private StockMovementType movementType;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal value;
    private LocalDate date;
}
```

- `recentLosses` includes LOSS and DAMAGE movement types from the last 30 days
- `highTransitValue` = sum of `Batch.transitQuantity * Batch.costPrice` across all batches
- `pendingTransfers` = count of transfers in DRAFT, IN_TRANSIT, or PENDING_VALIDATION status

### 4. `GET /api/reports/dashboard/movement-trend`

Movement volume over time for chart rendering.

**Parameters:** `days` (default: 30)

**Response:** `ApiResponse<MovementTrendResponse>`

```java
public class MovementTrendResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<DailyMovement> days;
    private MovementTotals totals;
}

public class DailyMovement {
    private LocalDate date;
    private BigDecimal totalInQuantity;
    private BigDecimal totalInValue;
    private BigDecimal totalOutQuantity;
    private BigDecimal totalOutValue;
    private Long movementCount;
}

public class MovementTotals {
    private BigDecimal totalInQuantity;
    private BigDecimal totalInValue;
    private BigDecimal totalOutQuantity;
    private BigDecimal totalOutValue;
    private Long movementCount;
}
```

- Each day aggregates all IN-direction and OUT-direction movements
- Values derived from `StockMovementItem.quantity` and `Batch.costPrice`
- Days with no movements are included with zero values

## New Repository Queries

### StockMovementRepository

```java
// Sum movements by direction for a period (for KPIs)
@Query("SELECT sm.direction, SUM(smi.quantity), SUM(smi.quantity * b.costPrice) " +
       "FROM StockMovement sm JOIN sm.items smi JOIN Batch b ON b.id = smi.batchId " +
       "WHERE sm.tenantId = :tenantId " +
       "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
       "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate " +
       "GROUP BY sm.direction")
List<Object[]> sumMovementsByDirectionAndPeriod(
    @Param("tenantId") UUID tenantId,
    @Param("warehouseId") UUID warehouseId,
    @Param("startDate") LocalDateTime startDate,
    @Param("endDate") LocalDateTime endDate);

// Sum movements by type for a period (for detailed KPIs)
@Query("SELECT sm.type, sm.direction, SUM(smi.quantity), SUM(smi.quantity * b.costPrice) " +
       "FROM StockMovement sm JOIN sm.items smi JOIN Batch b ON b.id = smi.batchId " +
       "WHERE sm.tenantId = :tenantId " +
       "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
       "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate " +
       "GROUP BY sm.type, sm.direction")
List<Object[]> sumMovementsByTypeAndPeriod(
    @Param("tenantId") UUID tenantId,
    @Param("warehouseId") UUID warehouseId,
    @Param("startDate") LocalDateTime startDate,
    @Param("endDate") LocalDateTime endDate);

// Count today's movements
@Query("SELECT COUNT(sm) FROM StockMovement sm " +
       "WHERE sm.tenantId = :tenantId " +
       "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
       "AND sm.createdAt >= :startOfDay AND sm.createdAt < :endOfDay")
long countTodayMovements(
    @Param("tenantId") UUID tenantId,
    @Param("warehouseId") UUID warehouseId,
    @Param("startOfDay") LocalDateTime startOfDay,
    @Param("endOfDay") LocalDateTime endOfDay);

// Daily movement trend
@Query("SELECT CAST(sm.createdAt AS date), sm.direction, SUM(smi.quantity), COUNT(DISTINCT sm) " +
       "FROM StockMovement sm JOIN sm.items smi " +
       "WHERE sm.tenantId = :tenantId " +
       "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
       "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate " +
       "GROUP BY CAST(sm.createdAt AS date), sm.direction " +
       "ORDER BY CAST(sm.createdAt AS date)")
List<Object[]> getDailyMovementTrend(
    @Param("tenantId") UUID tenantId,
    @Param("warehouseId") UUID warehouseId,
    @Param("startDate") LocalDateTime startDate,
    @Param("endDate") LocalDateTime endDate);

// Recent loss/damage movements
@Query("SELECT sm.type, smi.productName, smi.quantity, sm.createdAt " +
       "FROM StockMovement sm JOIN sm.items smi JOIN Batch b ON b.id = smi.batchId " +
       "WHERE sm.tenantId = :tenantId " +
       "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
       "AND sm.type IN :lossTypes " +
       "AND sm.createdAt >= :since " +
       "ORDER BY sm.createdAt DESC")
List<Object[]> findRecentLosses(
    @Param("tenantId") UUID tenantId,
    @Param("warehouseId") UUID warehouseId,
    @Param("lossTypes") List<StockMovementType> lossTypes,
    @Param("since") LocalDateTime since);
```

### BatchRepository

```java
// Sum stock value (costPrice * quantity)
@Query("SELECT COALESCE(SUM(b.costPrice * b.quantity), 0) FROM Batch b " +
       "WHERE b.tenantId = :tenantId AND b.deletedAt IS NULL " +
       "AND (:warehouseId IS NULL OR b.warehouse.id = :warehouseId)")
BigDecimal sumStockValue(
    @Param("tenantId") UUID tenantId,
    @Param("warehouseId") UUID warehouseId);

// Sum transit quantity
@Query("SELECT COALESCE(SUM(b.transitQuantity), 0) FROM Batch b " +
       "WHERE b.tenantId = :tenantId AND b.deletedAt IS NULL " +
       "AND (:warehouseId IS NULL OR b.warehouse.id = :warehouseId)")
BigDecimal sumTransitQuantity(
    @Param("tenantId") UUID tenantId,
    @Param("warehouseId") UUID warehouseId);

// Count active batches
@Query("SELECT COUNT(b) FROM Batch b " +
       "WHERE b.tenantId = :tenantId AND b.deletedAt IS NULL " +
       "AND (:warehouseId IS NULL OR b.warehouse.id = :warehouseId)")
long countActiveBatches(
    @Param("tenantId") UUID tenantId,
    @Param("warehouseId") UUID warehouseId);

// Count critical alerts (low stock + expiring soon)
@Query("SELECT COUNT(DISTINCT b.product.id) FROM Batch b " +
       "WHERE b.tenantId = :tenantId AND b.deletedAt IS NULL " +
       "AND (:warehouseId IS NULL OR b.warehouse.id = :warehouseId) " +
       "AND (b.quantity <= :threshold " +
       "OR (b.expirationDate IS NOT NULL AND b.expirationDate BETWEEN CURRENT_DATE AND :expirationLimit))")
long countCriticalAlerts(
    @Param("tenantId") UUID tenantId,
    @Param("warehouseId") UUID warehouseId,
    @Param("threshold") Integer threshold,
    @Param("expirationLimit") LocalDate expirationLimit);
```

### TransferRepository

```java
// Count pending transfers
@Query("SELECT COUNT(t) FROM Transfer t " +
       "WHERE t.tenantId = :tenantId " +
       "AND t.status IN :pendingStatuses " +
       "AND (:warehouseId IS NULL " +
       "OR t.sourceWarehouseId = :warehouseId " +
       "OR t.destinationWarehouseId = :warehouseId)")
long countPendingTransfers(
    @Param("tenantId") UUID tenantId,
    @Param("warehouseId") UUID warehouseId,
    @Param("pendingStatuses") List<TransferStatus> pendingStatuses);
```

## Authorization

All endpoints use the same permission as the existing dashboard:
- `@PreAuthorize("@permissionGuard.hasAny('reports:read')")`
- Tenant context via `TenantContext.getTenantId()`
- Warehouse filtering via `SecurityUtils.getCurrentWarehouseId()`

## Backward Compatibility

The existing `GET /api/reports/dashboard` endpoint is preserved. The new endpoints are additions under `/api/reports/dashboard/*`. The old endpoint can be deprecated later but is not removed.

## Testing

Integration tests for each endpoint:
- `GET /dashboard/summary` - verify totals with seeded data
- `GET /dashboard/kpis` - verify month comparison with movements in two months
- `GET /dashboard/alerts` - verify alerts with low stock, expiring, and loss data
- `GET /dashboard/movement-trend` - verify daily aggregation over 30 days

## Files to Create/Modify

**New DTOs:**
- `DashboardSummaryResponse`
- `DashboardKpisResponse`, `KpiPeriodData`, `KpiVariations`
- `DashboardAlertsResponse`, `RecentMovementAlert`
- `MovementTrendResponse`, `DailyMovement`, `MovementTotals`

**Modified Repositories:**
- `StockMovementRepository` - add 5 new queries
- `BatchRepository` - add 4 new queries
- `TransferRepository` - add 1 new query

**Modified Services:**
- `ReportService` - add 4 new methods for each dashboard endpoint

**Modified Controllers:**
- `ReportController` - add 4 new endpoints under `/dashboard/*`
