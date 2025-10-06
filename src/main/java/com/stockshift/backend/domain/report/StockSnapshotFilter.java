package com.stockshift.backend.domain.report;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public record StockSnapshotFilter(
        UUID warehouseId,
        UUID productId,
        UUID categoryId,
        UUID brandId,
        UUID variantId,
        String sku,
        Set<UUID> attributeValueIds,
        boolean aggregateByWarehouse,
        boolean includeZero,
        OffsetDateTime asOf
) {

    public StockSnapshotFilter {
        attributeValueIds = attributeValueIds == null ? Collections.emptySet() : Set.copyOf(attributeValueIds);
    }
}
