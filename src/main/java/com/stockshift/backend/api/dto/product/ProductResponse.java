package com.stockshift.backend.api.dto.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID brandId;
    private String brandName;
    private UUID categoryId;
    private String categoryName;
    private Long basePrice;
    private LocalDate expiryDate;
    private Boolean expired;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
