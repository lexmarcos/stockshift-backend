package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.DiscrepancyResolution;
import br.com.stockshift.model.enums.DiscrepancyStatus;
import br.com.stockshift.model.enums.DiscrepancyType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_discrepancy")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class NewTransferDiscrepancy extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_item_id", nullable = false)
    private TransferItem transferItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, length = 20)
    private DiscrepancyType discrepancyType;

    @Column(name = "expected_quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal expectedQuantity;

    @Column(name = "received_quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal receivedQuantity;

    @Column(name = "difference", nullable = false, precision = 15, scale = 3)
    private BigDecimal difference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DiscrepancyStatus status = DiscrepancyStatus.PENDING_RESOLUTION;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 30)
    private DiscrepancyResolution resolution;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
