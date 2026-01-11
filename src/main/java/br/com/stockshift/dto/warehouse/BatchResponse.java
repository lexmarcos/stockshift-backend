package br.com.stockshift.dto.warehouse;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private UUID warehouseId;
    private String warehouseName;
    private String batchCode;
    private Integer quantity;
    private LocalDate manufacturedDate;
    private LocalDate expirationDate;
    @Schema(description = "Cost price in cents", example = "1050")
    private Long costPrice;
    @Schema(description = "Selling price in cents", example = "1575")
    private Long sellingPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
