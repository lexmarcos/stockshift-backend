package br.com.stockshift.dto.warehouse;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchSellingPriceUpdateRequest {

    @NotNull(message = "Selling price is required")
    @PositiveOrZero(message = "Selling price must be zero or positive")
    @Schema(description = "Selling price in cents", example = "1575")
    private Long sellingPrice;
}
