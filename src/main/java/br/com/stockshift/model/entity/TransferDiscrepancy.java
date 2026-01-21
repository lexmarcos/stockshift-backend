package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transfer_discrepancies")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class TransferDiscrepancy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_validation_id", nullable = false)
    private TransferValidation transferValidation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_movement_item_id", nullable = false)
    private StockMovementItem stockMovementItem;

    @Column(name = "expected_quantity", nullable = false)
    private Integer expectedQuantity;

    @Column(name = "received_quantity", nullable = false)
    private Integer receivedQuantity;

    @Column(name = "missing_quantity", nullable = false)
    private Integer missingQuantity;

    @Column(name = "excess_quantity", nullable = false)
    private Integer excessQuantity;
}
