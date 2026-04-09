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
