package com.stockshift.backend.api.dto.attribute;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAttributeValueRequest {

    @Size(max = 100, message = "Value must not exceed 100 characters")
    private String value;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Swatch hex must be in format #RRGGBB")
    @Size(max = 7)
    private String swatchHex;
}
