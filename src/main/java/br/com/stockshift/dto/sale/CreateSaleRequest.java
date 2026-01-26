package br.com.stockshift.dto.sale;

import br.com.stockshift.model.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSaleRequest {
    
    @NotNull(message = "Warehouse ID is required")
    private Long warehouseId;
    
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;
    
    private Long customerId;
    
    private String customerName;
    
    @PositiveOrZero(message = "Discount must be zero or positive")
    private BigDecimal discount;
    
    private String notes;
    
    @NotEmpty(message = "Sale must have at least one item")
    @Valid
    private List<SaleItemRequest> items;
}
