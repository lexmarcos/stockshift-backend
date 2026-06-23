package br.com.stockshift.dto.warehouse;

import java.util.UUID;

public record BatchSellingPriceUpdateResponse(
    String message,
    int affectedCount,
    UUID productId,
    UUID warehouseId
) {}
