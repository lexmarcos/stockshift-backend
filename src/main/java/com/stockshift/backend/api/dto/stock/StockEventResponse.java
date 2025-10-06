package com.stockshift.backend.api.dto.stock;

import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockEventResponse {
    private UUID id;
    private StockEventType type;
    private UUID warehouseId;
    private String warehouseCode;
    private OffsetDateTime occurredAt;
    private StockReasonCode reasonCode;
    private String notes;
    private UUID createdById;
    private String createdByUsername;
    private OffsetDateTime createdAt;
    private List<StockEventLineResponse> lines;
}
