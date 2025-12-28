package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReportResponse {
    private UUID productId;
    private String productName;
    private UUID warehouseId;
    private String warehouseName;
    private Integer totalQuantity;
    private BigDecimal totalValue;
    private LocalDate nearestExpiration;
    private Integer batchCount;
}
