package br.com.stockshift.dto.product;

import br.com.stockshift.model.enums.BarcodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {
    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name cannot exceed 255 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s\\-\\.&'()]+$",
             message = "Product name contains invalid characters")
    private String name;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s\\-\\.&'(),!?:;]*$",
             message = "Description contains invalid characters")
    private String description;
    private UUID categoryId;
    private UUID brandId;

    @Pattern(regexp = "^[a-zA-Z0-9\\-]*$", message = "Barcode contains invalid characters")
    private String barcode;
    private BarcodeType barcodeType;

    @Pattern(regexp = "^[a-zA-Z0-9\\-]*$", message = "SKU contains invalid characters")
    private String sku;
    @Builder.Default
    private Boolean isKit = false;
    private Map<String, Object> attributes;
    @Builder.Default
    private Boolean hasExpiration = false;
    @Builder.Default
    private Boolean active = true;

    @Size(max = 500, message = "Image URL cannot exceed 500 characters")
    @Pattern(regexp = "^(https?://.*)?$", message = "Image URL must be a valid URL (http or https)")
    private String imageUrl;
}
