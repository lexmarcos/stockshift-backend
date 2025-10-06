package com.stockshift.backend.api.dto.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull(message = "originWarehouseId must not be null")
        UUID originWarehouseId,

        @NotNull(message = "destinationWarehouseId must not be null")
        UUID destinationWarehouseId,

        OffsetDateTime occurredAt,

        @Size(max = 500, message = "notes must have at most 500 characters")
        String notes,

        @NotEmpty(message = "lines must not be empty")
        @Valid
        List<CreateTransferLineRequest> lines
) {
}
