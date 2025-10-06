package com.stockshift.backend.api.dto.stock;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateStockEventLineRequest(
        @NotNull(message = "variantId must not be null")
        UUID variantId,

        @NotNull(message = "quantity must not be null")
        Long quantity
) {
}
