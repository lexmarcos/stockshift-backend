package br.com.stockshift.dto.sale;

import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.SaleStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleResponse {
    private UUID id;
    private String code;
    private UUID warehouseId;
    private String warehouseName;
    private PaymentMethod paymentMethod;
    private Integer installments;
    private BigDecimal discountPercentage;
    private Long subtotal;
    private Long discountAmount;
    private Long total;
    private SaleStatus status;
    private UUID cancelledByUserId;
    private Instant cancelledAt;
    private String cancellationReason;
    private UUID createdByUserId;
    private Instant createdAt;
    private List<SaleItemResponse> items;
    private String infinitepayNsu;
    private String infinitepayAut;
    private String infinitepayCardBrand;
}
