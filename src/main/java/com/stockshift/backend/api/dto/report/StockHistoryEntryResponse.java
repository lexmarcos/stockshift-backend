package com.stockshift.backend.api.dto.report;

import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockHistoryEntryResponse {

    private UUID eventId;
    private StockEventType eventType;
    private UUID warehouseId;
    private String warehouseName;
    private OffsetDateTime occurredAt;
    private long quantityChange;
    private long balanceBefore;
    private long balanceAfter;
    private StockReasonCode reasonCode;
    private String notes;
}
