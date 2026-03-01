package br.com.stockshift.dto.stockmovement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseMovementSummaryResponse {

  private List<WarehouseSummary> warehouses;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class WarehouseSummary {
    private UUID warehouseId;
    private String warehouseName;
    private List<TypeSummary> movementsByType;
    private BigDecimal totalIn;
    private BigDecimal totalOut;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TypeSummary {
    private String type;
    private String direction;
    private BigDecimal totalQuantity;
    private long count;
  }
}
