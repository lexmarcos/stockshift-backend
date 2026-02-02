package br.com.stockshift.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanBarcodeResponse {

    private boolean valid;
    private String message;
    private String warning;
    private String productName;
    private String productBarcode;
    private BigDecimal quantitySent;
    private BigDecimal quantityReceived;
}
