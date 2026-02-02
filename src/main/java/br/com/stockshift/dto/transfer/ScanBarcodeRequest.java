package br.com.stockshift.dto.transfer;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanBarcodeRequest {

    @NotBlank(message = "Barcode is required")
    private String barcode;
}
