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
