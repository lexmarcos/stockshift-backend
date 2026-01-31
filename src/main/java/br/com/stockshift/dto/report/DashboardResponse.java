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
public class DashboardResponse {
    private Long totalProducts;
    private Long totalWarehouses;
    private BigDecimal totalStockQuantity;
    private BigDecimal totalStockValue;
    private List<StockReportResponse> lowStockProducts;
    private List<StockReportResponse> expiringProducts;
}
