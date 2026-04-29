package br.com.stockshift.mapper;

import br.com.stockshift.dto.stockmovement.StockMovementResponse;
import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.entity.StockMovementItem;
import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockMovementMapperTest {

    private final StockMovementMapper mapper = new StockMovementMapper();

    @Test
    void toResponseShouldMapMovementItemsAndTimestamps() {
        StockMovement movement = movement();
        movement.addItem(item());

        StockMovementResponse response = mapper.toResponse(movement, "Main");

        assertThat(response.getId()).isEqualTo(movement.getId());
        assertThat(response.getWarehouseName()).isEqualTo("Main");
        assertThat(response.getType()).isEqualTo(StockMovementType.PURCHASE_IN);
        assertThat(response.getDirection()).isEqualTo(MovementDirection.IN);
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductName()).isEqualTo("Product");
    }

    @Test
    void toItemResponseListShouldMapAllItems() {
        assertThat(mapper.toItemResponseList(List.of(item(), item()))).hasSize(2);
    }

    private StockMovement movement() {
        StockMovement movement = StockMovement.builder()
                .code("MOV-2026-0001")
                .warehouseId(UUID.randomUUID())
                .type(StockMovementType.PURCHASE_IN)
                .direction(MovementDirection.IN)
                .notes("notes")
                .createdByUserId(UUID.randomUUID())
                .referenceType("REF")
                .referenceId(UUID.randomUUID())
                .build();
        movement.setId(UUID.randomUUID());
        movement.setCreatedAt(LocalDateTime.now());
        movement.setUpdatedAt(LocalDateTime.now());
        return movement;
    }

    private StockMovementItem item() {
        StockMovementItem item = StockMovementItem.builder()
                .productId(UUID.randomUUID())
                .productName("Product")
                .productSku("SKU")
                .batchId(UUID.randomUUID())
                .batchCode("B1")
                .quantity(new BigDecimal("2"))
                .build();
        item.setId(UUID.randomUUID());
        return item;
    }
}
