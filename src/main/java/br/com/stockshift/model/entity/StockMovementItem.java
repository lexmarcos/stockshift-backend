package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movement_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stock_movement_id", nullable = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private StockMovement stockMovement;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "product_name", nullable = false)
  private String productName;

  @Column(name = "product_sku")
  private String productSku;

  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  @Column(name = "batch_code", nullable = false)
  private String batchCode;

  @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantity;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
