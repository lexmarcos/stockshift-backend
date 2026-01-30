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
public class TransferSummaryResponse {
    private UUID id;
    private String transferCode;
    private TransferStatus status;
    private WarehouseSummary sourceWarehouse;
    private WarehouseSummary destinationWarehouse;
    private TransferRole direction;
    private List<TransferAction> allowedActions;
    private int itemCount;
    private BigDecimal totalQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime completedAt;
}
