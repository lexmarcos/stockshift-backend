package com.stockshift.backend.domain.report;

import java.util.UUID;

public record LowStockView(
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
        long threshold,
        long deficit
) {
}
