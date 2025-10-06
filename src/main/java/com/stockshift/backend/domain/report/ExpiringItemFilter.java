package com.stockshift.backend.domain.report;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public record ExpiringItemFilter(
        UUID warehouseId,
        UUID productId,
        UUID categoryId,
        UUID brandId,
        String sku,
        Set<UUID> attributeValueIds,
        LocalDate asOfDate,
        int daysAhead,
        boolean includeExpired,
        boolean aggregateByWarehouse
) {

    public ExpiringItemFilter {
        attributeValueIds = attributeValueIds == null ? Collections.emptySet() : Set.copyOf(attributeValueIds);
    }
}
