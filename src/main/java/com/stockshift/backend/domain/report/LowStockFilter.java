package com.stockshift.backend.domain.report;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public record LowStockFilter(
        UUID warehouseId,
        UUID productId,
        UUID categoryId,
        UUID brandId,
        String sku,
        Set<UUID> attributeValueIds,
        long threshold
) {

    public LowStockFilter {
        attributeValueIds = attributeValueIds == null ? Collections.emptySet() : Set.copyOf(attributeValueIds);
    }
}
