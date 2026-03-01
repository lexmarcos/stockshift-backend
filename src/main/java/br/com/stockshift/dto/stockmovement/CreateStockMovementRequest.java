package br.com.stockshift.dto.stockmovement;

import br.com.stockshift.model.enums.StockMovementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStockMovementRequest {

  @NotNull(message = "Movement type is required")
  private StockMovementType type;

  private String notes;

  @NotEmpty(message = "At least one item is required")
  @Valid
  private List<CreateStockMovementItemRequest> items;
}
