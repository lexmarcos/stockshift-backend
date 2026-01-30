package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferValidationResponse {
    private UUID transferId;
    private TransferStatus status;
    private LocalDateTime validationStartedAt;
    private UserSummary validationStartedBy;
    private List<TransferItemResponse> items;

    // Validation summary
    private int totalItems;
    private int itemsScanned;
    private int itemsPending;
    private boolean hasDiscrepancy;
    private boolean canComplete;

    private List<TransferAction> allowedActions;
}
