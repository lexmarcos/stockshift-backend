package com.stockshift.backend.api.mapper;

import com.stockshift.backend.api.dto.stock.StockEventLineResponse;
import com.stockshift.backend.api.dto.stock.StockEventResponse;
import com.stockshift.backend.domain.stock.StockEvent;
import com.stockshift.backend.domain.stock.StockEventLine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class StockEventMapper {

    public StockEventResponse toResponse(StockEvent event) {
        StockEventResponse response = new StockEventResponse();
        response.setId(event.getId());
        response.setType(event.getType());
        if (event.getWarehouse() != null) {
            response.setWarehouseId(event.getWarehouse().getId());
            response.setWarehouseCode(event.getWarehouse().getCode());
        }
        response.setOccurredAt(event.getOccurredAt());
        response.setReasonCode(event.getReasonCode());
        response.setNotes(event.getNotes());
        if (event.getCreatedBy() != null) {
            response.setCreatedById(event.getCreatedBy().getId());
            response.setCreatedByUsername(event.getCreatedBy().getUsername());
        }
        response.setCreatedAt(event.getCreatedAt());
        response.setLines(mapLines(event.getLines()));
        return response;
    }

    private List<StockEventLineResponse> mapLines(List<StockEventLine> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream()
                .map(this::toLineResponse)
                .collect(Collectors.toList());
    }

    private StockEventLineResponse toLineResponse(StockEventLine line) {
        StockEventLineResponse response = new StockEventLineResponse();
        response.setId(line.getId());
        if (line.getVariant() != null) {
            response.setVariantId(line.getVariant().getId());
            response.setVariantSku(line.getVariant().getSku());
        }
        response.setQuantity(line.getQuantity());
        return response;
    }
}
