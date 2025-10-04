package com.stockshift.backend.api.dto.product;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private UUID brandId;

    private UUID categoryId;

    @NotNull(message = "Base price is required")
    @Min(value = 1, message = "Base price must be greater than 0")
    private Long basePrice;

    @Future(message = "Expiry date must be in the future")
    private LocalDate expiryDate;
}
