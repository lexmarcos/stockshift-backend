package com.stockshift.backend.domain.report;

import java.time.LocalDate;
import java.util.UUID;

public record ExpiringItemView(
        UUID variantId,
        String sku,
        UUID productId,
        String productName,
        UUID brandId,
        String brandName,
        UUID categoryId,
        String categoryName,
        UUID warehouseId,
        String warehouseName,
        long quantity,
        LocalDate expiryDate,
        long daysUntilExpiry
) {
}
