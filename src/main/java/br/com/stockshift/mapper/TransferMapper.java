package br.com.stockshift.mapper;

import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferItem;
import br.com.stockshift.model.entity.TransferValidationLog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TransferMapper {

    public TransferResponse toResponse(Transfer transfer, String sourceWarehouseName, String destinationWarehouseName) {
        return TransferResponse.builder()
                .id(transfer.getId())
                .code(transfer.getCode())
                .sourceWarehouseId(transfer.getSourceWarehouseId())
                .sourceWarehouseName(sourceWarehouseName)
                .destinationWarehouseId(transfer.getDestinationWarehouseId())
                .destinationWarehouseName(destinationWarehouseName)
                .status(transfer.getStatus())
                .notes(transfer.getNotes())
                .createdByUserId(transfer.getCreatedByUserId())
                .executedByUserId(transfer.getExecutedByUserId())
                .executedAt(transfer.getExecutedAt())
                .validatedByUserId(transfer.getValidatedByUserId())
                .validatedAt(transfer.getValidatedAt())
                .cancelledByUserId(transfer.getCancelledByUserId())
                .cancelledAt(transfer.getCancelledAt())
                .cancellationReason(transfer.getCancellationReason())
                .createdAt(transfer.getCreatedAt() != null ? transfer.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
                .updatedAt(transfer.getUpdatedAt() != null ? transfer.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
                .items(toItemResponseList(transfer.getItems()))
                .build();
    }

    public TransferItemResponse toItemResponse(TransferItem item) {
        return TransferItemResponse.builder()
                .id(item.getId())
                .sourceBatchId(item.getSourceBatchId())
                .productId(item.getProductId())
                .productBarcode(item.getProductBarcode())
                .productName(item.getProductName())
                .productSku(item.getProductSku())
                .quantitySent(item.getQuantitySent())
                .quantityReceived(item.getQuantityReceived())
                .destinationBatchId(item.getDestinationBatchId())
                .build();
    }

    public List<TransferItemResponse> toItemResponseList(List<TransferItem> items) {
        return items.stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());
    }

    public ValidationLogResponse toValidationLogResponse(TransferValidationLog log) {
        return ValidationLogResponse.builder()
                .id(log.getId())
                .transferItemId(log.getTransferItemId())
                .barcode(log.getBarcode())
                .validatedByUserId(log.getValidatedByUserId())
                .validatedAt(log.getValidatedAt())
                .valid(log.getValid())
                .build();
    }

    public List<ValidationLogResponse> toValidationLogResponseList(List<TransferValidationLog> logs) {
        return logs.stream()
                .map(this::toValidationLogResponse)
                .collect(Collectors.toList());
    }
}
