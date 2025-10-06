package com.stockshift.backend.api.dto.transfer;

import com.stockshift.backend.domain.stock.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {
    private UUID id;
    private UUID originWarehouseId;
    private String originWarehouseCode;
    private UUID destinationWarehouseId;
    private String destinationWarehouseCode;
    private TransferStatus status;
    private OffsetDateTime occurredAt;
    private String notes;
    private UUID createdById;
    private String createdByUsername;
    private OffsetDateTime createdAt;
    private UUID confirmedById;
    private String confirmedByUsername;
    private OffsetDateTime confirmedAt;
    private UUID outboundEventId;
    private UUID inboundEventId;
    private List<TransferLineResponse> lines;
}
