package br.com.stockshift.dto.sale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Long batchId;
    private String batchCode;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
