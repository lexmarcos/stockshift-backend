package com.stockshift.backend.infrastructure.repository.specification;

import com.stockshift.backend.domain.stock.StockEvent;
import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class StockEventSpecifications {

    private StockEventSpecifications() {
    }

    public static Specification<StockEvent> hasType(StockEventType type) {
        return (root, query, criteriaBuilder) -> type == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("type"), type);
    }

    public static Specification<StockEvent> hasWarehouse(UUID warehouseId) {
        return (root, query, criteriaBuilder) -> warehouseId == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("warehouse").get("id"), warehouseId);
    }

    public static Specification<StockEvent> hasVariant(UUID variantId) {
        return (root, query, criteriaBuilder) -> {
            if (variantId == null) {
                return criteriaBuilder.conjunction();
            }
            query.distinct(true);
            var lines = root.join("lines", JoinType.INNER);
            return criteriaBuilder.equal(lines.get("variant").get("id"), variantId);
        };
    }

    public static Specification<StockEvent> occurredAfter(OffsetDateTime from) {
        return (root, query, criteriaBuilder) -> from == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), from);
    }

    public static Specification<StockEvent> occurredBefore(OffsetDateTime to) {
        return (root, query, criteriaBuilder) -> to == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), to);
    }

    public static Specification<StockEvent> hasReason(StockReasonCode reasonCode) {
        return (root, query, criteriaBuilder) -> reasonCode == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("reasonCode"), reasonCode);
    }
}
