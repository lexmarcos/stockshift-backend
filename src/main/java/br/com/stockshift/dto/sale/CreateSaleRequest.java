package br.com.stockshift.dto.sale;

import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSaleRequest {
    @NotNull(message = "Warehouse is required")
    private UUID warehouseId;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private Integer installments;

    private BigDecimal discountPercentage;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<CreateSaleItemRequest> items;

    private Boolean useInfinitePay;

    private PaymentMode paymentMode;
}
