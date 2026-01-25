package br.com.stockshift.dto.warehouse;

import java.util.UUID;

public record BatchDeletionResponse(
    String message,
    Integer deletedCount,
    UUID productId,
    UUID warehouseId
) {}
