package com.stockshift.backend.api.dto.transfer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateTransferLineRequest(
        @NotNull(message = "variantId must not be null")
        UUID variantId,

        @NotNull(message = "quantity must not be null")
        @Positive(message = "quantity must be positive")
        Long quantity
) {
}
