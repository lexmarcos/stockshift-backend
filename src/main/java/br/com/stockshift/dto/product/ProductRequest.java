package br.com.stockshift.dto.product;

import br.com.stockshift.model.enums.BarcodeType;
import jakarta.validation.constraints.NotBlank;
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
    private String name;

    private String description;
    private UUID categoryId;
    private String barcode;
    private BarcodeType barcodeType;
    private String sku;
    @Builder.Default
    private Boolean isKit = false;
    private Map<String, Object> attributes;
    @Builder.Default
    private Boolean hasExpiration = false;
    @Builder.Default
    private Boolean active = true;
}
