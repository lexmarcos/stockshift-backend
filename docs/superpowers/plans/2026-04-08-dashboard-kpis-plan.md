# Dashboard KPIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the dashboard into a comprehensive KPI panel with 4 sub-endpoints: summary, financial KPIs with month comparison, alerts, and movement trends.

**Architecture:** Add JPQL aggregation queries to existing repositories, new DTOs for each response, new methods in `ReportService`, and 4 new endpoints in `ReportController`. All follow existing patterns (tenant isolation, warehouse context, `ApiResponse<T>` wrapper).

**Tech Stack:** Spring Boot, Spring Data JPA (JPQL queries), Testcontainers (integration tests)

---

## File Structure

**New DTOs (create):**
- `src/main/java/br/com/stockshift/dto/report/DashboardSummaryResponse.java`
- `src/main/java/br/com/stockshift/dto/report/DashboardKpisResponse.java`
- `src/main/java/br/com/stockshift/dto/report/KpiPeriodData.java`
- `src/main/java/br/com/stockshift/dto/report/KpiVariations.java`
- `src/main/java/br/com/stockshift/dto/report/DashboardAlertsResponse.java`
- `src/main/java/br/com/stockshift/dto/report/RecentMovementAlert.java`
- `src/main/java/br/com/stockshift/dto/report/MovementTrendResponse.java`
- `src/main/java/br/com/stockshift/dto/report/DailyMovement.java`
- `src/main/java/br/com/stockshift/dto/report/MovementTotals.java`

**Modified repositories:**
- `src/main/java/br/com/stockshift/repository/StockMovementRepository.java` — add 5 queries
- `src/main/java/br/com/stockshift/repository/BatchRepository.java` — add 4 queries
- `src/main/java/br/com/stockshift/repository/TransferRepository.java` — add 1 query

**Modified service:**
- `src/main/java/br/com/stockshift/service/ReportService.java` — add 4 methods

**Modified controller:**
- `src/main/java/br/com/stockshift/controller/ReportController.java` — add 4 endpoints

**Test:**
- `src/test/java/br/com/stockshift/controller/ReportControllerIntegrationTest.java` — add 4 test methods + test data setup for movements/transfers

---

### Task 1: Create Dashboard DTOs

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/report/DashboardSummaryResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/report/KpiPeriodData.java`
- Create: `src/main/java/br/com/stockshift/dto/report/KpiVariations.java`
- Create: `src/main/java/br/com/stockshift/dto/report/DashboardKpisResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/report/RecentMovementAlert.java`
- Create: `src/main/java/br/com/stockshift/dto/report/DashboardAlertsResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/report/DailyMovement.java`
- Create: `src/main/java/br/com/stockshift/dto/report/MovementTotals.java`
- Create: `src/main/java/br/com/stockshift/dto/report/MovementTrendResponse.java`

- [ ] **Step 1: Create all DTO files**

`DashboardSummaryResponse.java`:
```java
package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

`KpiPeriodData.java`:
```java
package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
```

`KpiVariations.java`:
```java
package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiVariations {
    private BigDecimal totalStockValue;
    private BigDecimal totalPurchasesValue;
    private BigDecimal totalLossesValue;
    private BigDecimal totalDamageValue;
    private BigDecimal totalGiftValue;
    private BigDecimal totalAdjustmentValue;
    private BigDecimal totalTransitValue;
    private BigDecimal stockTurnoverRate;
}
```

`DashboardKpisResponse.java`:
```java
package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardKpisResponse {
    private KpiPeriodData currentMonth;
    private KpiPeriodData previousMonth;
    private KpiVariations variations;
}
```

`RecentMovementAlert.java`:
```java
package br.com.stockshift.dto.report;

import br.com.stockshift.model.enums.StockMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentMovementAlert {
    private StockMovementType movementType;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal value;
    private LocalDate date;
}
```

`DashboardAlertsResponse.java`:
```java
package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardAlertsResponse {
    private List<StockReportResponse> lowStockProducts;
    private List<StockReportResponse> expiringProducts;
    private List<RecentMovementAlert> recentLosses;
    private Long pendingTransfers;
    private BigDecimal highTransitValue;
}
```

`DailyMovement.java`:
```java
package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyMovement {
    private LocalDate date;
    private BigDecimal totalInQuantity;
    private BigDecimal totalInValue;
    private BigDecimal totalOutQuantity;
    private BigDecimal totalOutValue;
    private Long movementCount;
}
```

`MovementTotals.java`:
```java
package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementTotals {
    private BigDecimal totalInQuantity;
    private BigDecimal totalInValue;
    private BigDecimal totalOutQuantity;
    private BigDecimal totalOutValue;
    private Long movementCount;
}
```

`MovementTrendResponse.java`:
```java
package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementTrendResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<DailyMovement> days;
    private MovementTotals totals;
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/report/
git commit -m "feat(dashboard): add dashboard KPI response DTOs"
```

---

### Task 2: Add Repository Queries

**Files:**
- Modify: `src/main/java/br/com/stockshift/repository/StockMovementRepository.java`
- Modify: `src/main/java/br/com/stockshift/repository/BatchRepository.java`
- Modify: `src/main/java/br/com/stockshift/repository/TransferRepository.java`

- [ ] **Step 1: Add queries to StockMovementRepository**

Add these 5 new query methods to `StockMovementRepository.java` (after the existing `findExtract` method):

```java
    @Query("SELECT sm.type, sm.direction, COALESCE(SUM(smi.quantity), 0) " +
            "FROM StockMovement sm JOIN sm.items smi " +
            "WHERE sm.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate " +
            "GROUP BY sm.type, sm.direction")
    List<Object[]> sumMovementsByTypeAndPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(sm) FROM StockMovement sm " +
            "WHERE sm.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND sm.createdAt >= :startOfDay AND sm.createdAt < :endOfDay")
    long countTodayMovements(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT CAST(sm.createdAt AS LocalDate), sm.direction, COALESCE(SUM(smi.quantity), 0), COUNT(DISTINCT sm) " +
            "FROM StockMovement sm JOIN sm.items smi " +
            "WHERE sm.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate " +
            "GROUP BY CAST(sm.createdAt AS LocalDate), sm.direction " +
            "ORDER BY CAST(sm.createdAt AS LocalDate)")
    List<Object[]> getDailyMovementTrend(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT sm.type, smi.productName, smi.quantity, sm.createdAt, smi.batchId " +
            "FROM StockMovement sm JOIN sm.items smi " +
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

    @Query("SELECT COALESCE(SUM(smi.quantity), 0) FROM StockMovement sm JOIN sm.items smi " +
            "WHERE sm.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND sm.direction = br.com.stockshift.model.enums.MovementDirection.OUT " +
            "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate")
    BigDecimal sumOutQuantityForPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
```

Also add the import at the top:
```java
import java.math.BigDecimal;
```

- [ ] **Step 2: Add queries to BatchRepository**

Add these 4 new query methods to `BatchRepository.java` (after the existing `findByProductAndWarehouseForFifo` method):

```java
  @Query("SELECT COALESCE(SUM(CAST(b.costPrice AS bigdecimal) * b.quantity), 0) FROM Batch b " +
        "WHERE b.tenantId = :tenantId AND b.deletedAt IS NULL " +
        "AND (:warehouseId IS NULL OR b.warehouse.id = :warehouseId)")
  BigDecimal sumStockValue(
        @Param("tenantId") UUID tenantId,
        @Param("warehouseId") UUID warehouseId);

  @Query("SELECT COALESCE(SUM(b.transitQuantity), 0) FROM Batch b " +
        "WHERE b.tenantId = :tenantId AND b.deletedAt IS NULL " +
        "AND (:warehouseId IS NULL OR b.warehouse.id = :warehouseId)")
  BigDecimal sumTransitQuantity(
        @Param("tenantId") UUID tenantId,
        @Param("warehouseId") UUID warehouseId);

  @Query("SELECT COUNT(b) FROM Batch b " +
        "WHERE b.tenantId = :tenantId AND b.deletedAt IS NULL " +
        "AND (:warehouseId IS NULL OR b.warehouse.id = :warehouseId)")
  long countActiveBatches(
        @Param("tenantId") UUID tenantId,
        @Param("warehouseId") UUID warehouseId);

  @Query("SELECT COUNT(DISTINCT b.product.id) FROM Batch b " +
        "WHERE b.tenantId = :tenantId AND b.deletedAt IS NULL " +
        "AND (:warehouseId IS NULL OR b.warehouse.id = :warehouseId) " +
        "AND (b.quantity <= :threshold " +
        "OR (b.expirationDate IS NOT NULL AND b.expirationDate BETWEEN CURRENT_DATE AND :expirationLimit))")
  long countCriticalAlerts(
        @Param("tenantId") UUID tenantId,
        @Param("warehouseId") UUID warehouseId,
        @Param("threshold") BigDecimal threshold,
        @Param("expirationLimit") LocalDate expirationLimit);
```

Also add the import:
```java
import java.math.BigDecimal;
```

- [ ] **Step 3: Add query to TransferRepository**

Add this query method to `TransferRepository.java` (after the existing `countByTenantIdAndCodePrefix` method):

```java
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

Also add the import:
```java
import java.util.List;
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/StockMovementRepository.java \
        src/main/java/br/com/stockshift/repository/BatchRepository.java \
        src/main/java/br/com/stockshift/repository/TransferRepository.java
git commit -m "feat(dashboard): add repository queries for dashboard KPIs"
```

---

### Task 3: Implement ReportService Methods

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/ReportService.java`

- [ ] **Step 1: Add new dependencies and methods to ReportService**

Add the `StockMovementRepository` and `TransferRepository` dependencies. Add the 4 new service methods: `getSummary()`, `getKpis()`, `getAlerts()`, `getMovementTrend()`.

Add imports at the top:
```java
import br.com.stockshift.dto.report.*;
import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.StockMovementRepository;
import br.com.stockshift.repository.TransferRepository;
import br.com.stockshift.repository.WarehouseRepository;
```

Add fields to the class:
```java
    private final StockMovementRepository stockMovementRepository;
    private final TransferRepository transferRepository;
    private final WarehouseRepository warehouseRepository;
```

Add the `resolveWarehouseId()` helper (reuse existing logic but make it more broadly available). The existing `resolveCurrentWarehouseId()` is already private - keep it.

Add these 4 methods:

```java
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

        BigDecimal transitQuantity = batchRepository.sumTransitQuantity(tenantId, warehouseId);
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
            turnoverRate = totalOutQuantity.divide(currentStockValue, 2, java.math.RoundingMode.HALF_UP);
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
                .divide(previous, 2, java.math.RoundingMode.HALF_UP);
    }

    private void resolveOrFail(UUID warehouseId) {
        if (warehouseId == null && !warehouseAccessService.hasFullAccess()) {
            throw new UnauthorizedException("No active warehouse context");
        }
    }
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/service/ReportService.java
git commit -m "feat(dashboard): add service methods for summary, kpis, alerts, and movement trend"
```

---

### Task 4: Add Controller Endpoints

**Files:**
- Modify: `src/main/java/br/com/stockshift/controller/ReportController.java`

- [ ] **Step 1: Add 4 new endpoints to ReportController**

Add imports:
```java
import br.com.stockshift.dto.report.DashboardAlertsResponse;
import br.com.stockshift.dto.report.DashboardKpisResponse;
import br.com.stockshift.dto.report.DashboardSummaryResponse;
import br.com.stockshift.dto.report.MovementTrendResponse;
```

Add these 4 endpoints after the existing `getExpiringProductsReport` method:

```java
    @GetMapping("/dashboard/summary")
    @PreAuthorize("@permissionGuard.hasAny('reports:read')")
    @Operation(summary = "Get dashboard quick summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getDashboardSummary() {
        DashboardSummaryResponse response = reportService.getSummary();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/dashboard/kpis")
    @PreAuthorize("@permissionGuard.hasAny('reports:read')")
    @Operation(summary = "Get dashboard financial KPIs with month comparison")
    public ResponseEntity<ApiResponse<DashboardKpisResponse>> getDashboardKpis() {
        DashboardKpisResponse response = reportService.getKpis();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/dashboard/alerts")
    @PreAuthorize("@permissionGuard.hasAny('reports:read')")
    @Operation(summary = "Get dashboard operational alerts")
    public ResponseEntity<ApiResponse<DashboardAlertsResponse>> getDashboardAlerts() {
        DashboardAlertsResponse response = reportService.getAlerts();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/dashboard/movement-trend")
    @PreAuthorize("@permissionGuard.hasAny('reports:read')")
    @Operation(summary = "Get movement trend for chart")
    public ResponseEntity<ApiResponse<MovementTrendResponse>> getMovementTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        MovementTrendResponse response = reportService.getMovementTrend(days);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/ReportController.java
git commit -m "feat(dashboard): add summary, kpis, alerts, and movement-trend endpoints"
```

---

### Task 5: Add Integration Tests

**Files:**
- Modify: `src/test/java/br/com/stockshift/controller/ReportControllerIntegrationTest.java`
- Modify: `src/test/java/br/com/stockshift/util/TestDataFactory.java`

- [ ] **Step 1: Add helper methods to TestDataFactory**

Add `StockMovement` factory methods to `TestDataFactory.java`:

```java
    public static StockMovement createStockMovement(StockMovementRepository repo, UUID tenantId,
                                                     UUID warehouseId, StockMovementType type,
                                                     MovementDirection direction, UUID createdByUserId) {
        StockMovement sm = new StockMovement();
        sm.setTenantId(tenantId);
        sm.setCode("SM-" + UUID.randomUUID().toString().substring(0, 8));
        sm.setWarehouseId(warehouseId);
        sm.setType(type);
        sm.setDirection(direction);
        sm.setCreatedByUserId(createdByUserId);
        return repo.save(sm);
    }

    public static StockMovementItem createStockMovementItem(StockMovementItemRepository repo,
                                                             StockMovement movement, Product product,
                                                             Batch batch, BigDecimal quantity) {
        StockMovementItem item = new StockMovementItem();
        item.setStockMovement(movement);
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductSku(product.getSku());
        item.setBatchId(batch.getId());
        item.setBatchCode(batch.getBatchCode());
        item.setQuantity(quantity);
        return repo.save(item);
    }

    public static Transfer createTransfer(TransferRepository repo, UUID tenantId,
                                           UUID sourceWarehouseId, UUID destinationWarehouseId,
                                           TransferStatus status, UUID createdByUserId) {
        Transfer transfer = new Transfer();
        transfer.setTenantId(tenantId);
        transfer.setCode("TR-" + UUID.randomUUID().toString().substring(0, 8));
        transfer.setSourceWarehouseId(sourceWarehouseId);
        transfer.setDestinationWarehouseId(destinationWarehouseId);
        transfer.setStatus(status);
        transfer.setCreatedByUserId(createdByUserId);
        return repo.save(transfer);
    }
```

Also add the imports:
```java
import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.StockMovementItemRepository;
import br.com.stockshift.repository.StockMovementRepository;
import br.com.stockshift.repository.TransferRepository;
```

- [ ] **Step 2: Add test methods to ReportControllerIntegrationTest**

Add these fields and test methods:

```java
    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockMovementItemRepository stockMovementItemRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Test
    @WithMockUser(username = "report@test.com", authorities = { "ROLE_ADMIN" })
    void shouldGetDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalProducts").exists())
                .andExpect(jsonPath("$.data.totalWarehouses").exists())
                .andExpect(jsonPath("$.data.totalActiveBatches").exists())
                .andExpect(jsonPath("$.data.totalStockQuantity").exists())
                .andExpect(jsonPath("$.data.totalStockValue").exists())
                .andExpect(jsonPath("$.data.totalTransitQuantity").exists())
                .andExpect(jsonPath("$.data.pendingTransfers").exists())
                .andExpect(jsonPath("$.data.todayMovements").exists())
                .andExpect(jsonPath("$.data.criticalAlerts").exists());
    }

    @Test
    @WithMockUser(username = "report@test.com", authorities = { "ROLE_ADMIN" })
    void shouldGetDashboardKpis() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentMonth").exists())
                .andExpect(jsonPath("$.data.currentMonth.totalStockValue").exists())
                .andExpect(jsonPath("$.data.currentMonth.stockTurnoverRate").exists());
    }

    @Test
    @WithMockUser(username = "report@test.com", authorities = { "ROLE_ADMIN" })
    void shouldGetDashboardAlerts() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lowStockProducts").isArray())
                .andExpect(jsonPath("$.data.expiringProducts").isArray())
                .andExpect(jsonPath("$.data.recentLosses").isArray())
                .andExpect(jsonPath("$.data.pendingTransfers").exists())
                .andExpect(jsonPath("$.data.highTransitValue").exists());
    }

    @Test
    @WithMockUser(username = "report@test.com", authorities = { "ROLE_ADMIN" })
    void shouldGetMovementTrend() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard/movement-trend")
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.startDate").exists())
                .andExpect(jsonPath("$.data.endDate").exists())
                .andExpect(jsonPath("$.data.days").isArray())
                .andExpect(jsonPath("$.data.totals").exists())
                .andExpect(jsonPath("$.data.totals.totalInQuantity").exists())
                .andExpect(jsonPath("$.data.totals.totalOutQuantity").exists());
    }
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `./gradlew test --tests "br.com.stockshift.controller.ReportControllerIntegrationTest" -i`
Expected: All tests PASS

- [ ] **Step 4: Run full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/ReportControllerIntegrationTest.java \
        src/test/java/br/com/stockshift/util/TestDataFactory.java
git commit -m "test(dashboard): add integration tests for summary, kpis, alerts, and movement-trend endpoints"
```

---

### Task 6: Final Verification

- [ ] **Step 1: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run full test suite**

Run: `./gradlew test`
Expected: All tests pass
