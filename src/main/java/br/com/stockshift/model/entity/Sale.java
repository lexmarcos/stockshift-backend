package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.SaleStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sales", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "code"})
})
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"items", "cancelledByUserId", "cancelledAt", "cancellationReason"})
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "items")
public class Sale extends TenantAwareEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column
    private Integer installments;

    @Column
    private BigDecimal discountPercentage;

    @Column(nullable = false)
    private Long subtotal;

    @Column(nullable = false)
    private Long discountAmount;

    @Column(nullable = false)
    private Long total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Column
    private Instant cancelledAt;

    @Column(columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    public void addItem(SaleItem item) {
        items.add(item);
        item.setSale(this);
    }
}
