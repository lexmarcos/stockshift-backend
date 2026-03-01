package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_movements", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "tenant_id", "code" })
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement extends TenantAwareEntity {

  @Column(nullable = false, length = 50)
  private String code;

  @Column(name = "warehouse_id", nullable = false)
  private UUID warehouseId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private StockMovementType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private MovementDirection direction;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "reference_type", length = 50)
  private String referenceType;

  @Column(name = "reference_id")
  private UUID referenceId;

  @OneToMany(mappedBy = "stockMovement", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<StockMovementItem> items = new ArrayList<>();

  public void addItem(StockMovementItem item) {
    items.add(item);
    item.setStockMovement(this);
  }
}
