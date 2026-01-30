package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.DiscrepancyResolution;
import br.com.stockshift.model.enums.DiscrepancyStatus;
import br.com.stockshift.model.enums.DiscrepancyType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DiscrepancyResponse(
    UUID id,
    UUID transferId,
    UUID transferItemId,
    DiscrepancyType discrepancyType,
    BigDecimal expectedQuantity,
    BigDecimal receivedQuantity,
    BigDecimal difference,
    DiscrepancyStatus status,
    DiscrepancyResolution resolution,
    String resolutionNotes,
    UUID resolvedBy,
    LocalDateTime resolvedAt,
    LocalDateTime createdAt
) {}
