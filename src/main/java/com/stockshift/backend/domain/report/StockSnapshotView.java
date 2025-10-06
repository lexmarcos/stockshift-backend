package com.stockshift.backend.domain.report;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockSnapshotView(
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
        OffsetDateTime asOf
) {
}
