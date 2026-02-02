package br.com.stockshift.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferItemResponse {

    private UUID id;
    private UUID sourceBatchId;
    private UUID productId;
    private String productBarcode;
    private String productName;
    private String productSku;
    private BigDecimal quantitySent;
    private BigDecimal quantityReceived;
    private UUID destinationBatchId;
}
