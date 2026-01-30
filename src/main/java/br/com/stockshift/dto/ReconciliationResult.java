package br.com.stockshift.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReconciliationResult(
    UUID batchId,
    String batchCode,
    BigDecimal materializedQuantity,
    BigDecimal calculatedQuantity,
    BigDecimal difference
) {}
