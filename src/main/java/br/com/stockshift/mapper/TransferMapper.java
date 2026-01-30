package br.com.stockshift.mapper;

import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TransferMapper {

    public TransferResponse toResponse(Transfer transfer, TransferRole direction, List<TransferAction> allowedActions) {
        List<TransferItemResponse> items = transfer.getItems() != null
            ? transfer.getItems().stream().map(this::toItemResponse).collect(Collectors.toList())
            : List.of();

        BigDecimal totalExpected = items.stream()
            .map(TransferItemResponse::getExpectedQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReceived = items.stream()
            .map(i -> i.getReceivedQuantity() != null ? i.getReceivedQuantity() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int itemsValidated = (int) items.stream()
            .filter(i -> i.getReceivedQuantity() != null)
            .count();

        boolean hasDiscrepancy = items.stream()
            .anyMatch(i -> i.getReceivedQuantity() != null &&
                i.getReceivedQuantity().compareTo(i.getExpectedQuantity()) != 0);

        return TransferResponse.builder()
            .id(transfer.getId())
            .transferCode(transfer.getTransferCode())
            .status(transfer.getStatus())
            .sourceWarehouse(toWarehouseSummary(transfer.getSourceWarehouse()))
            .destinationWarehouse(toWarehouseSummary(transfer.getDestinationWarehouse()))
            .items(items)
            .direction(direction)
            .allowedActions(allowedActions)
            .totalItems(items.size())
            .totalExpectedQuantity(totalExpected)
            .totalReceivedQuantity(totalReceived)
            .itemsValidated(itemsValidated)
            .hasDiscrepancy(hasDiscrepancy)
            .createdBy(toUserSummary(transfer.getCreatedBy()))
            .createdAt(transfer.getCreatedAt())
            .dispatchedBy(toUserSummary(transfer.getDispatchedBy()))
            .dispatchedAt(transfer.getDispatchedAt())
            .validationStartedBy(toUserSummary(transfer.getValidationStartedBy()))
            .validationStartedAt(transfer.getValidationStartedAt())
            .completedBy(toUserSummary(transfer.getCompletedBy()))
            .completedAt(transfer.getCompletedAt())
            .cancelledBy(toUserSummary(transfer.getCancelledBy()))
            .cancelledAt(transfer.getCancelledAt())
            .cancellationReason(transfer.getCancellationReason())
            .notes(transfer.getNotes())
            .build();
    }

    public TransferSummaryResponse toSummaryResponse(Transfer transfer, TransferRole direction, List<TransferAction> allowedActions) {
        BigDecimal totalQuantity = transfer.getItems() != null
            ? transfer.getItems().stream()
                .map(TransferItem::getExpectedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
            : BigDecimal.ZERO;

        return TransferSummaryResponse.builder()
            .id(transfer.getId())
            .transferCode(transfer.getTransferCode())
            .status(transfer.getStatus())
            .sourceWarehouse(toWarehouseSummary(transfer.getSourceWarehouse()))
            .destinationWarehouse(toWarehouseSummary(transfer.getDestinationWarehouse()))
            .direction(direction)
            .allowedActions(allowedActions)
            .itemCount(transfer.getItems() != null ? transfer.getItems().size() : 0)
            .totalQuantity(totalQuantity)
            .createdAt(transfer.getCreatedAt())
            .dispatchedAt(transfer.getDispatchedAt())
            .completedAt(transfer.getCompletedAt())
            .build();
    }

    public TransferItemResponse toItemResponse(TransferItem item) {
        return TransferItemResponse.builder()
            .id(item.getId())
            .productId(item.getProduct().getId())
            .productName(item.getProduct().getName())
            .productSku(item.getProduct().getSku())
            .sourceBatchId(item.getSourceBatch().getId())
            .sourceBatchCode(item.getSourceBatch().getBatchCode())
            .destinationBatchId(item.getDestinationBatch() != null ? item.getDestinationBatch().getId() : null)
            .destinationBatchCode(item.getDestinationBatch() != null ? item.getDestinationBatch().getBatchCode() : null)
            .expectedQuantity(item.getExpectedQuantity())
            .receivedQuantity(item.getReceivedQuantity())
            .status(item.getStatus())
            .build();
    }

    public WarehouseSummary toWarehouseSummary(Warehouse warehouse) {
        if (warehouse == null) return null;
        return WarehouseSummary.builder()
            .id(warehouse.getId())
            .name(warehouse.getName())
            .code(warehouse.getCode())
            .build();
    }

    public UserSummary toUserSummary(User user) {
        if (user == null) return null;
        return UserSummary.builder()
            .id(user.getId())
            .name(user.getFullName())
            .email(user.getEmail())
            .build();
    }
}
