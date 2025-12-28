package br.com.stockshift.dto.movement;

import br.com.stockshift.model.enums.MovementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementRequest {
    @NotNull(message = "Movement type is required")
    private MovementType movementType;

    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;

    private String notes;

    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<StockMovementItemRequest> items;
}
