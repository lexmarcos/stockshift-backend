package com.stockshift.backend.api.dto.attribute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAttributeValueRequest {

    @NotBlank(message = "Value is required")
    @Size(max = 100, message = "Value must not exceed 100 characters")
    private String value;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
