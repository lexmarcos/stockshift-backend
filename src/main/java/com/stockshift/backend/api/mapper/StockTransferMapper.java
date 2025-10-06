package com.stockshift.backend.api.mapper;

import com.stockshift.backend.api.dto.transfer.TransferLineResponse;
import com.stockshift.backend.api.dto.transfer.TransferResponse;
import com.stockshift.backend.domain.stock.StockTransfer;
import com.stockshift.backend.domain.stock.StockTransferLine;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class StockTransferMapper {

    public TransferResponse toResponse(StockTransfer transfer) {
        TransferResponse response = new TransferResponse();
        response.setId(transfer.getId());
        response.setOriginWarehouseId(transfer.getOriginWarehouse().getId());
        response.setOriginWarehouseCode(transfer.getOriginWarehouse().getCode());
        response.setDestinationWarehouseId(transfer.getDestinationWarehouse().getId());
        response.setDestinationWarehouseCode(transfer.getDestinationWarehouse().getCode());
        response.setStatus(transfer.getStatus());
        response.setOccurredAt(transfer.getOccurredAt());
        response.setNotes(transfer.getNotes());
        response.setCreatedById(transfer.getCreatedBy().getId());
        response.setCreatedByUsername(transfer.getCreatedBy().getUsername());
        response.setCreatedAt(transfer.getCreatedAt());

        if (transfer.getConfirmedBy() != null) {
            response.setConfirmedById(transfer.getConfirmedBy().getId());
            response.setConfirmedByUsername(transfer.getConfirmedBy().getUsername());
        }
        response.setConfirmedAt(transfer.getConfirmedAt());

        if (transfer.getOutboundEvent() != null) {
            response.setOutboundEventId(transfer.getOutboundEvent().getId());
        }

        if (transfer.getInboundEvent() != null) {
            response.setInboundEventId(transfer.getInboundEvent().getId());
        }

        response.setLines(transfer.getLines().stream()
                .map(this::toLineResponse)
                .collect(Collectors.toList()));

        return response;
    }

    private TransferLineResponse toLineResponse(StockTransferLine line) {
        TransferLineResponse response = new TransferLineResponse();
        response.setId(line.getId());
        response.setVariantId(line.getVariant().getId());
        response.setVariantSku(line.getVariant().getSku());
        response.setQuantity(line.getQuantity());
        return response;
    }
}
