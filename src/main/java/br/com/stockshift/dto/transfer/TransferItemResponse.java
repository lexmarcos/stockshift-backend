package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferItemStatus;
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
    private UUID productId;
    private String productName;
    private String productSku;
    private UUID sourceBatchId;
    private String sourceBatchCode;
    private UUID destinationBatchId;
    private String destinationBatchCode;
    private BigDecimal expectedQuantity;
    private BigDecimal receivedQuantity;
    private TransferItemStatus status;
}
