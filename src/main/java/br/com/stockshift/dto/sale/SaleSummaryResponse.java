package br.com.stockshift.dto.sale;

import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.SaleStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleSummaryResponse {
    private UUID id;
    private String code;
    private UUID warehouseId;
    private String warehouseName;
    private PaymentMethod paymentMethod;
    private Long total;
    private SaleStatus status;
    private Instant createdAt;
}
