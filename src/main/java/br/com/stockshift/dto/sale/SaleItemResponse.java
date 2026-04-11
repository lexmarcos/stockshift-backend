package br.com.stockshift.dto.sale;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private String productSku;
    private UUID batchId;
    private String batchCode;
    private BigDecimal quantity;
    private Long unitPrice;
    private Long totalPrice;
}
