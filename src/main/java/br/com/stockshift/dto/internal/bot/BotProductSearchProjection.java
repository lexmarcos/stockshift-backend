package br.com.stockshift.dto.internal.bot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface BotProductSearchProjection {
    UUID getProductId();
    String getName();
    String getImageUrl();
    String getBarcode();
    String getSku();
    UUID getWarehouseId();
    String getWarehouseName();
    BigDecimal getTotalQuantity();
    Long getLatestBatchSellingPrice();
    String getLatestBatchCode();
    LocalDateTime getLatestBatchCreatedAt();
}
