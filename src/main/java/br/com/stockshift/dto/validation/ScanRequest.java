package br.com.stockshift.dto.validation;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanRequest {
    @NotBlank(message = "Barcode is required")
    private String barcode;
}
