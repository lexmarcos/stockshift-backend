package br.com.stockshift.dto.movement;

import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.model.enums.MovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {
    private UUID id;
    private MovementType movementType;
    private MovementStatus status;
    private UUID sourceWarehouseId;
    private String sourceWarehouseName;
    private UUID destinationWarehouseId;
    private String destinationWarehouseName;
    private UUID userId;
    private String userName;
    private String notes;
    private List<StockMovementItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
