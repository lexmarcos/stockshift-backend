package br.com.stockshift.dto.warehouse;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Schema(description = "Average cost price in cents", example = "1050")
    private Long avgCostPrice;

    @Schema(description = "Average selling price in cents", example = "1575")
    private Long avgSellingPrice;
}
