package com.stockshift.backend.api.dto.variant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductVariantRequest {

    @NotBlank(message = "SKU is required")
    @Size(max = 100, message = "SKU must not exceed 100 characters")
    private String sku;

    @Size(max = 100, message = "GTIN must not exceed 100 characters")
    private String gtin;

    @NotEmpty(message = "At least one attribute must be provided")
    private List<VariantAttributePair> attributes = new ArrayList<>();

    @Min(value = 0, message = "Price must be non-negative")
    private Long price;

    @Min(value = 0, message = "Weight must be non-negative")
    private Integer weight;

    @Min(value = 0, message = "Length must be non-negative")
    private Integer length;

    @Min(value = 0, message = "Width must be non-negative")
    private Integer width;

    @Min(value = 0, message = "Height must be non-negative")
    private Integer height;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantAttributePair {
        private UUID definitionId;
        private UUID valueId;
    }
}
