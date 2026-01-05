package br.com.stockshift.dto.warehouse;

import br.com.stockshift.model.enums.BarcodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatchRequest {
    // Product fields
    @NotBlank(message = "Product name is required")
    private String name;
    private String description;
    private UUID categoryId;
    private UUID brandId;
    private String barcode;
    private BarcodeType barcodeType;
    private String sku;
    @Builder.Default
    private Boolean isKit = false;
    private Map<String, Object> attributes;
    @Builder.Default
    private Boolean hasExpiration = false;
    private String imageUrl;

    // Batch fields
    @NotNull(message = "Warehouse ID is required")
    private UUID warehouseId;
    @NotBlank(message = "Batch code is required")
    private String batchCode;
    @NotNull(message = "Quantity is required")
    @PositiveOrZero(message = "Quantity must be zero or positive")
    private Integer quantity;
    private LocalDate manufacturedDate;
    private LocalDate expirationDate;
    @PositiveOrZero(message = "Cost price must be zero or positive")
    private BigDecimal costPrice;
    @PositiveOrZero(message = "Selling price must be zero or positive")
    private BigDecimal sellingPrice;
}
