package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private Long totalProducts;
    private Long activeProducts;
    private Long totalWarehouses;
    private Long activeWarehouses;
    private Long totalBatches;
    private BigDecimal totalStockValue;
    private Long lowStockCount;
    private Long expiringCount;
    private List<RecentMovement> recentMovements;
    private List<StockByWarehouse> stockByWarehouse;
    private List<StockByCategory> stockByCategory;
    private MovementStats movementStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentMovement {
        private UUID id;
        private String movementType;
        private String status;
        private java.time.LocalDateTime createdAt;
        private Integer productCount;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockByWarehouse {
        private UUID warehouseId;
        private String warehouseName;
        private Long batchCount;
        private BigDecimal stockValue;
        private Long productCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockByCategory {
        private String categoryId;
        private String categoryName;
        private Long batchCount;
        private BigDecimal stockValue;
        private Long productCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MovementStats {
        private MovementStatsPeriod today;
        private MovementStatsPeriod thisWeek;
        private MovementStatsPeriod thisMonth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MovementStatsPeriod {
        private Long entries;
        private Long exits;
        private Long transfers;
        private Long adjustments;
    }
}
