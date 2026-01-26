package br.com.stockshift.dto.sale;

import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.SaleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleResponse {
    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private Long userId;
    private String userName;
    private Long customerId;
    private String customerName;
    private PaymentMethod paymentMethod;
    private SaleStatus status;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal total;
    private String notes;
    private Long stockMovementId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private Long cancelledBy;
    private String cancelledByName;
    private String cancellationReason;
    private List<SaleItemResponse> items;
}
