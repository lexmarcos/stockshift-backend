package com.stockshift.backend.api.dto.variant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductVariantRequest {

    @Size(max = 100, message = "GTIN must not exceed 100 characters")
    private String gtin;

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
}
