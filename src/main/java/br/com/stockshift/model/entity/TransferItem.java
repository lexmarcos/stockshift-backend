package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.TransferItemStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "transfer_items")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class TransferItem extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_batch_id", nullable = false)
    private Batch sourceBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_batch_id")
    private Batch destinationBatch;

    @Column(name = "expected_quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal expectedQuantity;

    @Column(name = "received_quantity", precision = 15, scale = 3)
    private BigDecimal receivedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 30)
    private TransferItemStatus status = TransferItemStatus.PENDING;
}
