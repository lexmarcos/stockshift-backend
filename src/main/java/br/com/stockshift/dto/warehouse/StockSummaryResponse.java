package br.com.stockshift.dto.warehouse;

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
public class StockSummaryResponse {
    private UUID productId;
    private String productName;
    private UUID warehouseId;
    private String warehouseName;
    private Integer totalQuantity;
    private LocalDate nearestExpiration;
    private BigDecimal avgCostPrice;
    private BigDecimal avgSellingPrice;
}
