package br.com.stockshift.dto.warehouse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseStockSummaryResponse {
    private UUID warehouseId;
    private Long productCount;
    private Long batchCount;
    private BigDecimal totalQuantity;
}
