package com.stockshift.backend.domain.report;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public record StockHistoryFilter(
        UUID variantId,
        UUID productId,
        UUID warehouseId,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo,
        Set<UUID> attributeValueIds
) {

    public StockHistoryFilter {
        attributeValueIds = attributeValueIds == null ? Collections.emptySet() : Set.copyOf(attributeValueIds);
    }
}
