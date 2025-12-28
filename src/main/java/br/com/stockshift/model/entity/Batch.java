package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "batches", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "batch_code"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Batch extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "batch_code", nullable = false, length = 100)
    private String batchCode;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "manufactured_date")
    private LocalDate manufacturedDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "cost_price", precision = 15, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", precision = 15, scale = 2)
    private BigDecimal sellingPrice;
}
