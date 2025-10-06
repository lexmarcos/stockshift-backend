package com.stockshift.backend.api.dto.stock;

import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateStockEventRequest(
        @NotNull(message = "type must not be null")
        StockEventType type,

        @NotNull(message = "warehouseId must not be null")
        UUID warehouseId,

        OffsetDateTime occurredAt,

        StockReasonCode reasonCode,

        @Size(max = 500, message = "notes must have at most 500 characters")
        String notes,

        @NotEmpty(message = "lines must not be empty")
        @Valid
        List<CreateStockEventLineRequest> lines
) {
}
