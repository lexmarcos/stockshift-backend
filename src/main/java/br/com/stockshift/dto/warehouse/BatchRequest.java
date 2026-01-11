package br.com.stockshift.dto.warehouse;

import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRequest {
    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Warehouse ID is required")
    private UUID warehouseId;

    private String batchCode;

    @NotNull(message = "Quantity is required")
    @PositiveOrZero(message = "Quantity must be zero or positive")
    private Integer quantity;

    private LocalDate manufacturedDate;
    private LocalDate expirationDate;

    @Schema(description = "Cost price in cents", example = "1050")
    @PositiveOrZero(message = "Cost price must be zero or positive")
    private Long costPrice;

    @Schema(description = "Selling price in cents", example = "1575")
    @PositiveOrZero(message = "Selling price must be zero or positive")
    private Long sellingPrice;
}
