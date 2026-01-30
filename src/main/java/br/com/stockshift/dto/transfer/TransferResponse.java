package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.model.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {
    private UUID id;
    private String transferCode;
    private TransferStatus status;

    private WarehouseSummary sourceWarehouse;
    private WarehouseSummary destinationWarehouse;

    private List<TransferItemResponse> items;

    private TransferRole direction;
    private List<TransferAction> allowedActions;

    // Summary
    private int totalItems;
    private BigDecimal totalExpectedQuantity;
    private BigDecimal totalReceivedQuantity;
    private int itemsValidated;
    private boolean hasDiscrepancy;

    // Audit
    private UserSummary createdBy;
    private LocalDateTime createdAt;
    private UserSummary dispatchedBy;
    private LocalDateTime dispatchedAt;
    private UserSummary validationStartedBy;
    private LocalDateTime validationStartedAt;
    private UserSummary completedBy;
    private LocalDateTime completedAt;
    private UserSummary cancelledBy;
    private LocalDateTime cancelledAt;
    private String cancellationReason;

    private String notes;
}
