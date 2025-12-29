package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.BarcodeType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "products", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "tenant_id", "barcode" }),
        @UniqueConstraint(columnNames = { "tenant_id", "sku" })
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Product extends TenantAwareEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "barcode_type", length = 20)
    private BarcodeType barcodeType;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "is_kit", nullable = false)
    private Boolean isKit = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private java.util.Map<String, Object> attributes;

    @Column(name = "has_expiration", nullable = false)
    private Boolean hasExpiration = false;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;
}
