package br.com.stockshift.dto.stockmovement;

import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
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
public class StockMovementResponse {
  private UUID id;
  private String code;
  private UUID warehouseId;
  private String warehouseName;
  private StockMovementType type;
  private MovementDirection direction;
  private String notes;
  private UUID createdByUserId;
  private String referenceType;
  private UUID referenceId;
  private Instant createdAt;
  private Instant updatedAt;
  private List<StockMovementItemResponse> items;
}
