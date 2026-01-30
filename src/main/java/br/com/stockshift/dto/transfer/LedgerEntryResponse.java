package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.LedgerEntryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
    UUID id,
    UUID warehouseId,
    UUID productId,
    UUID batchId,
    LedgerEntryType entryType,
    boolean isDebit,
    BigDecimal quantity,
    BigDecimal balanceAfter,
    String referenceType,
    UUID referenceId,
    UUID transferItemId,
    String notes,
    UUID createdBy,
    LocalDateTime createdAt
) {}
