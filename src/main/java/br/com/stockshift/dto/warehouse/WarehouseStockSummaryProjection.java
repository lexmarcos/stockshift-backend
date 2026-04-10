package br.com.stockshift.dto.warehouse;

import java.math.BigDecimal;
import java.util.UUID;

public interface WarehouseStockSummaryProjection {
    UUID getWarehouseId();
    long getProductCount();
    long getBatchCount();
    BigDecimal getTotalQuantity();
}
