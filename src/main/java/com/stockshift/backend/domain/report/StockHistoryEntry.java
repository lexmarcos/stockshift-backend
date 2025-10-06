package com.stockshift.backend.domain.report;

import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockHistoryEntry(
        UUID eventId,
        StockEventType eventType,
        UUID warehouseId,
        String warehouseName,
        OffsetDateTime occurredAt,
        long quantityChange,
        long balanceBefore,
        long balanceAfter,
        StockReasonCode reasonCode,
        String notes
) {
}
