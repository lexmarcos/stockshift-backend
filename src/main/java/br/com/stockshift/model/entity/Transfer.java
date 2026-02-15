package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transfers", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "code"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transfer extends TenantAwareEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "source_warehouse_id", nullable = false)
    private UUID sourceWarehouseId;

    @Column(name = "destination_warehouse_id", nullable = false)
    private UUID destinationWarehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private TransferStatus status = TransferStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "executed_by_user_id")
    private UUID executedByUserId;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "validated_by_user_id")
    private UUID validatedByUserId;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Version
    private Long version;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TransferItem> items = new ArrayList<>();

    public void addItem(TransferItem item) {
        items.add(item);
        item.setTransfer(this);
    }

    public void removeItem(TransferItem item) {
        items.remove(item);
        item.setTransfer(null);
    }
}
