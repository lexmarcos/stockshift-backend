package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {

    private UUID id;
    private String code;
    private UUID sourceWarehouseId;
    private String sourceWarehouseName;
    private UUID destinationWarehouseId;
    private String destinationWarehouseName;
    private TransferStatus status;
    private String notes;
    private UUID createdByUserId;
    private UUID executedByUserId;
    private Instant executedAt;
    private UUID validatedByUserId;
    private Instant validatedAt;
    private UUID cancelledByUserId;
    private Instant cancelledAt;
    private String cancellationReason;
    private Instant createdAt;
    private Instant updatedAt;
    private List<TransferItemResponse> items;
}
