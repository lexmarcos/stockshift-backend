package com.stockshift.backend.api.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiringItemResponse {

    private UUID variantId;
    private String sku;
    private UUID productId;
    private String productName;
    private UUID brandId;
    private String brandName;
    private UUID categoryId;
    private String categoryName;
    private UUID warehouseId;
    private String warehouseName;
    private long quantity;
    private LocalDate expiryDate;
    private long daysUntilExpiry;
}
