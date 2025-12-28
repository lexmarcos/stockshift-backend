package br.com.stockshift.dto.movement;

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
public class StockMovementItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private UUID batchId;
    private String batchCode;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}
