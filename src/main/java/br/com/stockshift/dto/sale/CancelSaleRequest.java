package br.com.stockshift.dto.sale;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelSaleRequest {
    @NotBlank(message = "Cancellation reason is required")
    private String cancellationReason;
}
