package com.stockshift.backend.domain.attribute;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "attribute_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttributeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttributeType type;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean isVariantDefining = true;

    @Column(nullable = false)
    private Boolean isRequired = false;

    @ElementCollection
    @CollectionTable(name = "attribute_definition_categories", joinColumns = @JoinColumn(name = "definition_id"))
    @Column(name = "category_id")
    private List<UUID> applicableCategoryIds = new ArrayList<>();

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttributeValue> values = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttributeStatus status = AttributeStatus.ACTIVE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    public boolean isApplicableToCategory(UUID categoryId) {
        return applicableCategoryIds == null || applicableCategoryIds.isEmpty() || applicableCategoryIds.contains(categoryId);
    }

    public boolean isEnumType() {
        return type == AttributeType.ENUM || type == AttributeType.MULTI_ENUM;
    }
}
