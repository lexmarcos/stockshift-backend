package br.com.stockshift.dto.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationItemResponse {
    private UUID itemId;
    private UUID productId;
    private String productName;
    private String barcode;
    private Integer expectedQuantity;
    private Integer scannedQuantity;
    private String status;
}
