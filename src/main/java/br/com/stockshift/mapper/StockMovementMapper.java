package br.com.stockshift.mapper;

import br.com.stockshift.dto.stockmovement.StockMovementItemResponse;
import br.com.stockshift.dto.stockmovement.StockMovementResponse;
import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.entity.StockMovementItem;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StockMovementMapper {

  public StockMovementResponse toResponse(StockMovement movement, String warehouseName) {
    return StockMovementResponse.builder()
        .id(movement.getId())
        .code(movement.getCode())
        .warehouseId(movement.getWarehouseId())
        .warehouseName(warehouseName)
        .type(movement.getType())
        .direction(movement.getDirection())
        .notes(movement.getNotes())
        .createdByUserId(movement.getCreatedByUserId())
        .referenceType(movement.getReferenceType())
        .referenceId(movement.getReferenceId())
        .createdAt(movement.getCreatedAt() != null
            ? movement.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
            : null)
        .updatedAt(movement.getUpdatedAt() != null
            ? movement.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
            : null)
        .items(toItemResponseList(movement.getItems()))
        .build();
  }

  public StockMovementItemResponse toItemResponse(StockMovementItem item) {
    return StockMovementItemResponse.builder()
        .id(item.getId())
        .productId(item.getProductId())
        .productName(item.getProductName())
        .productSku(item.getProductSku())
        .batchId(item.getBatchId())
        .batchCode(item.getBatchCode())
        .quantity(item.getQuantity())
        .build();
  }

  public List<StockMovementItemResponse> toItemResponseList(List<StockMovementItem> items) {
    return items.stream()
        .map(this::toItemResponse)
        .collect(Collectors.toList());
  }
}
