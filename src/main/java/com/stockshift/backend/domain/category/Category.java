package com.stockshift.backend.domain.category;

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
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<Category> children = new ArrayList<>();

    @Column(nullable = false, length = 1000)
    private String path = "/";

    @Column(nullable = false)
    private Integer level = 0;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    public void updatePath() {
        if (parent == null) {
            this.path = "/" + this.id + "/";
            this.level = 0;
        } else {
            this.path = parent.getPath() + this.id + "/";
            this.level = parent.getLevel() + 1;
        }
    }

    public boolean isDescendantOf(Category category) {
        return this.path.startsWith(category.getPath()) && !this.id.equals(category.getId());
    }

    public boolean hasCircularReference(Category newParent) {
        if (newParent == null) {
            return false;
        }
        return newParent.getId().equals(this.id) || newParent.isDescendantOf(this);
    }
}
