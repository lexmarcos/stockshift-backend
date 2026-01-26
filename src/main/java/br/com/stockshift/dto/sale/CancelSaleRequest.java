package br.com.stockshift.dto.sale;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelSaleRequest {
    
    @NotBlank(message = "Cancellation reason is required")
    private String reason;
}
