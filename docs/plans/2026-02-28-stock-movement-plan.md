# StockMovement Module — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create a complete StockMovement module that serves as a unified log of all inventory movements (usage, gift, loss, damage, purchase, adjustment) with FIFO batch deduction, Transfer integration, and reporting endpoints.

**Architecture:** New `StockMovement` + `StockMovementItem` entities with tenant-scoped access. Movements are immediate (no approval workflow). The system uses FIFO to automatically debit batches. Transfer executions and validations also generate StockMovement records for a unified view.

**Tech Stack:** Java 17+, Spring Boot, JPA/Hibernate, PostgreSQL, Flyway, Lombok

---

### Task 1: Create Enums (StockMovementType + MovementDirection)

**Files:**
- Create: `src/main/java/br/com/stockshift/model/enums/StockMovementType.java`
- Create: `src/main/java/br/com/stockshift/model/enums/MovementDirection.java`
- Modify: `src/main/java/br/com/stockshift/model/enums/LedgerEntryType.java`

**Step 1: Create MovementDirection enum**

Create `src/main/java/br/com/stockshift/model/enums/MovementDirection.java`:

```java
package br.com.stockshift.model.enums;

public enum MovementDirection {
    IN,
    OUT
}
```

**Step 2: Create StockMovementType enum**

Create `src/main/java/br/com/stockshift/model/enums/StockMovementType.java`:

```java
package br.com.stockshift.model.enums;

public enum StockMovementType {
    USAGE(MovementDirection.OUT),
    GIFT(MovementDirection.OUT),
    LOSS(MovementDirection.OUT),
    DAMAGE(MovementDirection.OUT),
    PURCHASE_IN(MovementDirection.IN),
    ADJUSTMENT_IN(MovementDirection.IN),
    ADJUSTMENT_OUT(MovementDirection.OUT),
    TRANSFER_IN(MovementDirection.IN),
    TRANSFER_OUT(MovementDirection.OUT);

    private final MovementDirection direction;

    StockMovementType(MovementDirection direction) {
        this.direction = direction;
    }

    public MovementDirection getDirection() {
        return direction;
    }

    public boolean isDebit() {
        return direction == MovementDirection.OUT;
    }

    public boolean isCredit() {
        return direction == MovementDirection.IN;
    }
}
```

**Step 3: Add new LedgerEntryType values**

Modify `src/main/java/br/com/stockshift/model/enums/LedgerEntryType.java` — add these entries before the semicolon:

```java
    USAGE_OUT(true),
    GIFT_OUT(true),
    LOSS_OUT(true),
    DAMAGE_OUT(true),
    STOCK_MOVEMENT_IN(false),
    STOCK_MOVEMENT_OUT(true);
```

**Step 4: Compile check**

Run: `cd /home/lexmarcos/projects/stockshift-backend && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add -A && git commit -m "feat(stock-movement): add StockMovementType, MovementDirection enums and new LedgerEntryTypes"
```

---

### Task 2: Create Entities (StockMovement + StockMovementItem)

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/StockMovement.java`
- Create: `src/main/java/br/com/stockshift/model/entity/StockMovementItem.java`

**Step 1: Create StockMovement entity**

Create `src/main/java/br/com/stockshift/model/entity/StockMovement.java`:

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_movements", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "code"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement extends TenantAwareEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockMovementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MovementDirection direction;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @OneToMany(mappedBy = "stockMovement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockMovementItem> items = new ArrayList<>();

    public void addItem(StockMovementItem item) {
        items.add(item);
        item.setStockMovement(this);
    }
}
```

**Step 2: Create StockMovementItem entity**

Create `src/main/java/br/com/stockshift/model/entity/StockMovementItem.java`:

```java
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
```

**Step 3: Compile check**

Run: `cd /home/lexmarcos/projects/stockshift-backend && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add -A && git commit -m "feat(stock-movement): add StockMovement and StockMovementItem entities"
```

---

### Task 3: Create DTOs

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/stockmovement/CreateStockMovementRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/stockmovement/CreateStockMovementItemRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/stockmovement/StockMovementResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/stockmovement/StockMovementItemResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/stockmovement/WarehouseMovementSummaryResponse.java`

**Step 1: Create CreateStockMovementItemRequest**

Create `src/main/java/br/com/stockshift/dto/stockmovement/CreateStockMovementItemRequest.java`:

```java
package br.com.stockshift.dto.stockmovement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStockMovementItemRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;
}
```

**Step 2: Create CreateStockMovementRequest**

Create `src/main/java/br/com/stockshift/dto/stockmovement/CreateStockMovementRequest.java`:

```java
package br.com.stockshift.dto.stockmovement;

import br.com.stockshift.model.enums.StockMovementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStockMovementRequest {

    @NotNull(message = "Movement type is required")
    private StockMovementType type;

    private String notes;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<CreateStockMovementItemRequest> items;
}
```

**Step 3: Create StockMovementItemResponse**

Create `src/main/java/br/com/stockshift/dto/stockmovement/StockMovementItemResponse.java`:

```java
package br.com.stockshift.dto.stockmovement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private String productSku;
    private UUID batchId;
    private String batchCode;
    private BigDecimal quantity;
}
```

**Step 4: Create StockMovementResponse**

Create `src/main/java/br/com/stockshift/dto/stockmovement/StockMovementResponse.java`:

```java
package br.com.stockshift.dto.stockmovement;

import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {
    private UUID id;
    private String code;
    private UUID warehouseId;
    private String warehouseName;
    private StockMovementType type;
    private MovementDirection direction;
    private String notes;
    private UUID createdByUserId;
    private String referenceType;
    private UUID referenceId;
    private Instant createdAt;
    private Instant updatedAt;
    private List<StockMovementItemResponse> items;
}
```

**Step 5: Create WarehouseMovementSummaryResponse**

Create `src/main/java/br/com/stockshift/dto/stockmovement/WarehouseMovementSummaryResponse.java`:

```java
package br.com.stockshift.dto.stockmovement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseMovementSummaryResponse {

    private List<WarehouseSummary> warehouses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WarehouseSummary {
        private UUID warehouseId;
        private String warehouseName;
        private List<TypeSummary> movementsByType;
        private BigDecimal totalIn;
        private BigDecimal totalOut;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeSummary {
        private String type;
        private String direction;
        private BigDecimal totalQuantity;
        private long count;
    }
}
```

**Step 6: Compile check**

Run: `cd /home/lexmarcos/projects/stockshift-backend && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add -A && git commit -m "feat(stock-movement): add DTOs for stock movement module"
```

---

### Task 4: Create Repositories + Mapper

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/StockMovementRepository.java`
- Create: `src/main/java/br/com/stockshift/repository/StockMovementItemRepository.java`
- Create: `src/main/java/br/com/stockshift/mapper/StockMovementMapper.java`
- Modify: `src/main/java/br/com/stockshift/repository/BatchRepository.java` (add FIFO query)

**Step 1: Create StockMovementRepository**

Create `src/main/java/br/com/stockshift/repository/StockMovementRepository.java`:

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.enums.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Optional<StockMovement> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT sm FROM StockMovement sm WHERE sm.tenantId = :tenantId AND sm.warehouseId = :warehouseId ORDER BY sm.createdAt DESC")
    Page<StockMovement> findByTenantIdAndWarehouseId(
        @Param("tenantId") UUID tenantId,
        @Param("warehouseId") UUID warehouseId,
        Pageable pageable
    );

    @Query("SELECT sm FROM StockMovement sm WHERE sm.tenantId = :tenantId " +
           "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
           "AND (:type IS NULL OR sm.type = :type) " +
           "AND (:dateFrom IS NULL OR sm.createdAt >= :dateFrom) " +
           "AND (:dateTo IS NULL OR sm.createdAt <= :dateTo) " +
           "ORDER BY sm.createdAt DESC")
    Page<StockMovement> findWithFilters(
        @Param("tenantId") UUID tenantId,
        @Param("warehouseId") UUID warehouseId,
        @Param("type") StockMovementType type,
        @Param("dateFrom") LocalDateTime dateFrom,
        @Param("dateTo") LocalDateTime dateTo,
        Pageable pageable
    );

    @Query("SELECT sm FROM StockMovement sm LEFT JOIN FETCH sm.items WHERE sm.tenantId = :tenantId " +
           "AND sm.warehouseId IN :warehouseIds " +
           "AND (:dateFrom IS NULL OR sm.createdAt >= :dateFrom) " +
           "AND (:dateTo IS NULL OR sm.createdAt <= :dateTo)")
    List<StockMovement> findForWarehouseSummary(
        @Param("tenantId") UUID tenantId,
        @Param("warehouseIds") List<UUID> warehouseIds,
        @Param("dateFrom") LocalDateTime dateFrom,
        @Param("dateTo") LocalDateTime dateTo
    );

    @Query("SELECT sm.code FROM StockMovement sm WHERE sm.tenantId = :tenantId AND sm.code LIKE :prefix% ORDER BY sm.code DESC LIMIT 1")
    String findLatestCodeByTenantIdAndCodePrefix(@Param("tenantId") UUID tenantId, @Param("prefix") String prefix);

    @Query("SELECT COUNT(sm) FROM StockMovement sm WHERE sm.tenantId = :tenantId AND sm.code LIKE :prefix%")
    long countByTenantIdAndCodePrefix(@Param("tenantId") UUID tenantId, @Param("prefix") String prefix);

    @Query("SELECT sm FROM StockMovement sm JOIN FETCH sm.items i WHERE sm.tenantId = :tenantId " +
           "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
           "AND (:productId IS NULL OR i.productId = :productId) " +
           "AND (:type IS NULL OR sm.type = :type) " +
           "AND (:dateFrom IS NULL OR sm.createdAt >= :dateFrom) " +
           "AND (:dateTo IS NULL OR sm.createdAt <= :dateTo) " +
           "ORDER BY sm.createdAt DESC")
    Page<StockMovement> findExtract(
        @Param("tenantId") UUID tenantId,
        @Param("warehouseId") UUID warehouseId,
        @Param("productId") UUID productId,
        @Param("type") StockMovementType type,
        @Param("dateFrom") LocalDateTime dateFrom,
        @Param("dateTo") LocalDateTime dateTo,
        Pageable pageable
    );
}
```

**Step 2: Create StockMovementItemRepository**

Create `src/main/java/br/com/stockshift/repository/StockMovementItemRepository.java`:

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.StockMovementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementItemRepository extends JpaRepository<StockMovementItem, UUID> {
    List<StockMovementItem> findByStockMovementId(UUID stockMovementId);
}
```

**Step 3: Add FIFO query to BatchRepository**

Add this method to `src/main/java/br/com/stockshift/repository/BatchRepository.java` (inside the interface, before the closing brace):

```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Batch b WHERE b.product.id = :productId AND b.warehouse.id = :warehouseId " +
           "AND b.tenantId = :tenantId AND b.quantity > 0 ORDER BY b.createdAt ASC")
    List<Batch> findByProductAndWarehouseForFifo(
        @Param("productId") UUID productId,
        @Param("warehouseId") UUID warehouseId,
        @Param("tenantId") UUID tenantId
    );
```

**Step 4: Create StockMovementMapper**

Create `src/main/java/br/com/stockshift/mapper/StockMovementMapper.java`:

```java
package br.com.stockshift.mapper;

import br.com.stockshift.dto.stockmovement.StockMovementItemResponse;
import br.com.stockshift.dto.stockmovement.StockMovementResponse;
import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.entity.StockMovementItem;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StockMovementMapper {

    public StockMovementResponse toResponse(StockMovement movement, String warehouseName) {
        return StockMovementResponse.builder()
                .id(movement.getId())
                .code(movement.getCode())
                .warehouseId(movement.getWarehouseId())
                .warehouseName(warehouseName)
                .type(movement.getType())
                .direction(movement.getDirection())
                .notes(movement.getNotes())
                .createdByUserId(movement.getCreatedByUserId())
                .referenceType(movement.getReferenceType())
                .referenceId(movement.getReferenceId())
                .createdAt(movement.getCreatedAt() != null
                        ? movement.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                        : null)
                .updatedAt(movement.getUpdatedAt() != null
                        ? movement.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                        : null)
                .items(toItemResponseList(movement.getItems()))
                .build();
    }

    public StockMovementItemResponse toItemResponse(StockMovementItem item) {
        return StockMovementItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .productSku(item.getProductSku())
                .batchId(item.getBatchId())
                .batchCode(item.getBatchCode())
                .quantity(item.getQuantity())
                .build();
    }

    public List<StockMovementItemResponse> toItemResponseList(List<StockMovementItem> items) {
        return items.stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());
    }
}
```

**Step 5: Compile check**

Run: `cd /home/lexmarcos/projects/stockshift-backend && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add -A && git commit -m "feat(stock-movement): add repositories, mapper, and FIFO batch query"
```

---

### Task 5: Create StockMovementService (core logic + FIFO)

**Files:**
- Create: `src/main/java/br/com/stockshift/service/stockmovement/StockMovementService.java`

**Step 1: Create StockMovementService**

Create `src/main/java/br/com/stockshift/service/stockmovement/StockMovementService.java`:

```java
package br.com.stockshift.service.stockmovement;

import br.com.stockshift.dto.stockmovement.*;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.InsufficientStockException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.mapper.StockMovementMapper;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.LedgerEntryType;
import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.WarehouseAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockMovementService {

    private static final int CODE_SEQUENCE_PADDING = 4;
    private static final Set<StockMovementType> TRANSFER_TYPES = Set.of(
            StockMovementType.TRANSFER_IN, StockMovementType.TRANSFER_OUT
    );

    private final StockMovementRepository movementRepository;
    private final StockMovementItemRepository movementItemRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryLedgerRepository ledgerRepository;
    private final StockMovementMapper mapper;
    private final SecurityUtils securityUtils;
    private final WarehouseAccessService warehouseAccessService;

    // ── Manual movement (usage, gift, loss, etc.) ──────────────────────────

    @Transactional
    public StockMovementResponse create(CreateStockMovementRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID warehouseId = securityUtils.getCurrentWarehouseId();
        UUID userId = securityUtils.getCurrentUserId();

        // Prevent manual creation of transfer types
        if (TRANSFER_TYPES.contains(request.getType())) {
            throw new BadRequestException("Transfer movements are created automatically by the Transfer module");
        }

        String code = generateCode(tenantId);
        MovementDirection direction = request.getType().getDirection();

        StockMovement movement = StockMovement.builder()
                .code(code)
                .warehouseId(warehouseId)
                .type(request.getType())
                .direction(direction)
                .notes(request.getNotes())
                .createdByUserId(userId)
                .build();
        movement.setTenantId(tenantId);

        // Process each item
        for (CreateStockMovementItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findByTenantIdAndId(tenantId, itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemReq.getProductId()));

            if (direction == MovementDirection.OUT) {
                processOutItems(movement, product, itemReq.getQuantity(), warehouseId, tenantId, userId, request.getType());
            } else {
                processInItem(movement, product, itemReq.getQuantity(), warehouseId, tenantId, userId, request.getType());
            }
        }

        StockMovement saved = movementRepository.save(movement);
        log.info("StockMovement {} ({}) created by user {} in warehouse {}",
                saved.getCode(), saved.getType(), userId, warehouseId);

        String warehouseName = warehouseRepository.findById(warehouseId)
                .map(Warehouse::getName).orElse("Unknown");
        return mapper.toResponse(saved, warehouseName);
    }

    private void processOutItems(StockMovement movement, Product product, BigDecimal quantity,
                                  UUID warehouseId, UUID tenantId, UUID userId, StockMovementType type) {
        List<Batch> batches = batchRepository.findByProductAndWarehouseForFifo(
                product.getId(), warehouseId, tenantId);

        BigDecimal remaining = quantity;
        BigDecimal totalAvailable = batches.stream()
                .map(Batch::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAvailable.compareTo(quantity) < 0) {
            throw new InsufficientStockException(
                    "Insufficient stock for product '" + product.getName() +
                    "'. Available: " + totalAvailable + ", Required: " + quantity);
        }

        LedgerEntryType ledgerType = mapToLedgerType(type);

        for (Batch batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal deductAmount = remaining.min(batch.getQuantity());
            batch.setQuantity(batch.getQuantity().subtract(deductAmount));
            batchRepository.save(batch);

            StockMovementItem item = StockMovementItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .batchId(batch.getId())
                    .batchCode(batch.getBatchCode())
                    .quantity(deductAmount)
                    .build();
            movement.addItem(item);

            // Create ledger entry
            InventoryLedger ledger = InventoryLedger.builder()
                    .tenantId(tenantId)
                    .warehouseId(warehouseId)
                    .batchId(batch.getId())
                    .productId(product.getId())
                    .entryType(ledgerType)
                    .quantity(deductAmount.negate())
                    .balanceAfter(batch.getQuantity())
                    .referenceType("STOCK_MOVEMENT")
                    .referenceId(movement.getId())
                    .notes(type.name() + ": " + (movement.getNotes() != null ? movement.getNotes() : ""))
                    .createdBy(userId)
                    .build();
            ledgerRepository.save(ledger);

            remaining = remaining.subtract(deductAmount);
        }
    }

    private void processInItem(StockMovement movement, Product product, BigDecimal quantity,
                                UUID warehouseId, UUID tenantId, UUID userId, StockMovementType type) {
        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", warehouseId));

        // For IN movements, create a new batch or add to first existing batch
        List<Batch> existingBatches = batchRepository.findByProductIdAndWarehouseIdAndTenantId(
                product.getId(), warehouseId, tenantId);

        Batch batch;
        if (!existingBatches.isEmpty()) {
            batch = existingBatches.get(0);
            batch.setQuantity(batch.getQuantity().add(quantity));
            batchRepository.save(batch);
        } else {
            String batchCode = "MOV-" + LocalDate.now() + "-" + product.getSku();
            batch = Batch.builder()
                    .product(product)
                    .warehouse(warehouse)
                    .batchCode(batchCode)
                    .quantity(quantity)
                    .transitQuantity(BigDecimal.ZERO)
                    .build();
            batch.setTenantId(tenantId);
            batch = batchRepository.save(batch);
        }

        StockMovementItem item = StockMovementItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .productSku(product.getSku())
                .batchId(batch.getId())
                .batchCode(batch.getBatchCode())
                .quantity(quantity)
                .build();
        movement.addItem(item);

        LedgerEntryType ledgerType = mapToLedgerType(type);
        InventoryLedger ledger = InventoryLedger.builder()
                .tenantId(tenantId)
                .warehouseId(warehouseId)
                .batchId(batch.getId())
                .productId(product.getId())
                .entryType(ledgerType)
                .quantity(quantity)
                .balanceAfter(batch.getQuantity())
                .referenceType("STOCK_MOVEMENT")
                .referenceId(movement.getId())
                .notes(type.name() + ": " + (movement.getNotes() != null ? movement.getNotes() : ""))
                .createdBy(userId)
                .build();
        ledgerRepository.save(ledger);
    }

    // ── Transfer integration (called by TransferService) ───────────────────

    @Transactional
    public StockMovement createForTransfer(UUID tenantId, UUID warehouseId, UUID userId,
                                           StockMovementType type, UUID transferId,
                                           List<StockMovementItem> items, String notes) {
        String code = generateCode(tenantId);

        StockMovement movement = StockMovement.builder()
                .code(code)
                .warehouseId(warehouseId)
                .type(type)
                .direction(type.getDirection())
                .notes(notes)
                .createdByUserId(userId)
                .referenceType("TRANSFER")
                .referenceId(transferId)
                .build();
        movement.setTenantId(tenantId);

        for (StockMovementItem item : items) {
            movement.addItem(item);
        }

        StockMovement saved = movementRepository.save(movement);
        log.info("StockMovement {} ({}) created for transfer {} in warehouse {}",
                saved.getCode(), saved.getType(), transferId, warehouseId);
        return saved;
    }

    // ── Read operations ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public StockMovementResponse getById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        StockMovement movement = movementRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("StockMovement", "id", id));

        String warehouseName = warehouseRepository.findById(movement.getWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");
        return mapper.toResponse(movement, warehouseName);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> list(UUID warehouseId, UUID productId,
                                             StockMovementType type,
                                             LocalDateTime dateFrom, LocalDateTime dateTo,
                                             Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();

        // Use current warehouse if not specified
        UUID effectiveWarehouseId = warehouseId != null ? warehouseId : currentWarehouseId;

        Page<StockMovement> movements;
        if (productId != null) {
            movements = movementRepository.findExtract(tenantId, effectiveWarehouseId, productId, type, dateFrom, dateTo, pageable);
        } else {
            movements = movementRepository.findWithFilters(tenantId, effectiveWarehouseId, type, dateFrom, dateTo, pageable);
        }

        return movements.map(m -> {
            String name = warehouseRepository.findById(m.getWarehouseId())
                    .map(Warehouse::getName).orElse("Unknown");
            return mapper.toResponse(m, name);
        });
    }

    @Transactional(readOnly = true)
    public WarehouseMovementSummaryResponse getWarehouseSummary(LocalDateTime dateFrom, LocalDateTime dateTo) {
        UUID tenantId = TenantContext.getTenantId();

        // Get all warehouses for this tenant
        List<Warehouse> warehouses = warehouseRepository.findAllByTenantId(tenantId);
        List<UUID> warehouseIds = warehouses.stream().map(Warehouse::getId).collect(Collectors.toList());

        List<StockMovement> movements = movementRepository.findForWarehouseSummary(
                tenantId, warehouseIds, dateFrom, dateTo);

        // Group by warehouse
        Map<UUID, List<StockMovement>> byWarehouse = movements.stream()
                .collect(Collectors.groupingBy(StockMovement::getWarehouseId));

        Map<UUID, String> warehouseNames = warehouses.stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));

        List<WarehouseMovementSummaryResponse.WarehouseSummary> summaries = new ArrayList<>();

        for (Warehouse wh : warehouses) {
            List<StockMovement> whMovements = byWarehouse.getOrDefault(wh.getId(), Collections.emptyList());

            Map<StockMovementType, List<StockMovement>> byType = whMovements.stream()
                    .collect(Collectors.groupingBy(StockMovement::getType));

            BigDecimal totalIn = BigDecimal.ZERO;
            BigDecimal totalOut = BigDecimal.ZERO;
            List<WarehouseMovementSummaryResponse.TypeSummary> typeSummaries = new ArrayList<>();

            for (Map.Entry<StockMovementType, List<StockMovement>> entry : byType.entrySet()) {
                BigDecimal qty = entry.getValue().stream()
                        .flatMap(m -> m.getItems().stream())
                        .map(StockMovementItem::getQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (entry.getKey().getDirection() == MovementDirection.IN) {
                    totalIn = totalIn.add(qty);
                } else {
                    totalOut = totalOut.add(qty);
                }

                typeSummaries.add(WarehouseMovementSummaryResponse.TypeSummary.builder()
                        .type(entry.getKey().name())
                        .direction(entry.getKey().getDirection().name())
                        .totalQuantity(qty)
                        .count(entry.getValue().size())
                        .build());
            }

            summaries.add(WarehouseMovementSummaryResponse.WarehouseSummary.builder()
                    .warehouseId(wh.getId())
                    .warehouseName(wh.getName())
                    .movementsByType(typeSummaries)
                    .totalIn(totalIn)
                    .totalOut(totalOut)
                    .build());
        }

        return WarehouseMovementSummaryResponse.builder()
                .warehouses(summaries)
                .build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String generateCode(UUID tenantId) {
        String prefix = "MOV-" + LocalDate.now().getYear() + "-";
        String latestCode = movementRepository.findLatestCodeByTenantIdAndCodePrefix(tenantId, prefix);

        if (latestCode == null || latestCode.isBlank()) {
            return prefix + String.format("%0" + CODE_SEQUENCE_PADDING + "d", 1);
        }

        String sequencePart = latestCode.substring(prefix.length());
        try {
            long next = Long.parseLong(sequencePart) + 1;
            return prefix + String.format("%0" + CODE_SEQUENCE_PADDING + "d", next);
        } catch (NumberFormatException ex) {
            log.warn("Unexpected movement code format '{}' for tenant {}. Falling back to count-based sequence.",
                    latestCode, tenantId);
            long count = movementRepository.countByTenantIdAndCodePrefix(tenantId, prefix);
            return prefix + String.format("%0" + CODE_SEQUENCE_PADDING + "d", count + 1);
        }
    }

    private LedgerEntryType mapToLedgerType(StockMovementType type) {
        return switch (type) {
            case USAGE -> LedgerEntryType.USAGE_OUT;
            case GIFT -> LedgerEntryType.GIFT_OUT;
            case LOSS -> LedgerEntryType.LOSS_OUT;
            case DAMAGE -> LedgerEntryType.DAMAGE_OUT;
            case PURCHASE_IN -> LedgerEntryType.STOCK_MOVEMENT_IN;
            case ADJUSTMENT_IN -> LedgerEntryType.ADJUSTMENT_IN;
            case ADJUSTMENT_OUT -> LedgerEntryType.ADJUSTMENT_OUT;
            case TRANSFER_IN -> LedgerEntryType.TRANSFER_IN;
            case TRANSFER_OUT -> LedgerEntryType.TRANSFER_OUT;
        };
    }
}
```

**Step 2: Compile check**

Run: `cd /home/lexmarcos/projects/stockshift-backend && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add -A && git commit -m "feat(stock-movement): add StockMovementService with FIFO logic and transfer integration"
```

---

### Task 6: Create Controller

**Files:**
- Create: `src/main/java/br/com/stockshift/controller/StockMovementController.java`
- Modify: `src/main/java/br/com/stockshift/model/enums/PermissionResource.java` (add STOCK_MOVEMENT)

**Step 1: Add STOCK_MOVEMENT to PermissionResource**

Add this entry to `src/main/java/br/com/stockshift/model/enums/PermissionResource.java` before the semicolon:

```java
    STOCK_MOVEMENT("Movimentação de Estoque")
```

**Step 2: Create StockMovementController**

Create `src/main/java/br/com/stockshift/controller/StockMovementController.java`:

```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.stockmovement.*;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.service.stockmovement.StockMovementService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-movements")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class StockMovementController {

    private final StockMovementService stockMovementService;

    @PostMapping
    @PreAuthorize("@permissionGuard.hasAny('stock_movements:create')")
    public ResponseEntity<ApiResponse<StockMovementResponse>> create(
            @Valid @RequestBody CreateStockMovementRequest request) {
        StockMovementResponse response = stockMovementService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock movement created successfully", response));
    }

    @GetMapping
    @PreAuthorize("@permissionGuard.hasAny('stock_movements:read')")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            Pageable pageable) {
        Page<StockMovementResponse> response = stockMovementService.list(
                warehouseId, productId, type, dateFrom, dateTo, pageable);
        return ResponseEntity.ok(ApiResponse.success("Stock movements retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionGuard.hasAny('stock_movements:read')")
    public ResponseEntity<ApiResponse<StockMovementResponse>> getById(@PathVariable UUID id) {
        StockMovementResponse response = stockMovementService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Stock movement retrieved successfully", response));
    }

    @GetMapping("/warehouse-summary")
    @PreAuthorize("@permissionGuard.hasAny('stock_movements:read')")
    public ResponseEntity<ApiResponse<WarehouseMovementSummaryResponse>> warehouseSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        WarehouseMovementSummaryResponse response = stockMovementService.getWarehouseSummary(dateFrom, dateTo);
        return ResponseEntity.ok(ApiResponse.success("Warehouse summary retrieved successfully", response));
    }
}
```

**Step 3: Compile check**

Run: `cd /home/lexmarcos/projects/stockshift-backend && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add -A && git commit -m "feat(stock-movement): add StockMovementController with CRUD and report endpoints"
```

---

### Task 7: Create Flyway Migration

**Files:**
- Create: `src/main/resources/db/migration/V3__stock_movements.sql`

**Step 1: Create migration**

Create `src/main/resources/db/migration/V3__stock_movements.sql`:

```sql
-- Stock Movement module tables

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    warehouse_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    notes TEXT,
    created_by_user_id UUID NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_stock_movement_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_stock_movement_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_stock_movement_user FOREIGN KEY (created_by_user_id) REFERENCES users(id)
);

CREATE TABLE stock_movement_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_movement_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_sku VARCHAR(100),
    batch_id UUID NOT NULL,
    batch_code VARCHAR(100) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_smi_stock_movement FOREIGN KEY (stock_movement_id) REFERENCES stock_movements(id) ON DELETE CASCADE,
    CONSTRAINT fk_smi_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_smi_batch FOREIGN KEY (batch_id) REFERENCES batches(id)
);

-- Indexes for common queries
CREATE INDEX idx_stock_movements_tenant_warehouse ON stock_movements(tenant_id, warehouse_id);
CREATE INDEX idx_stock_movements_tenant_type ON stock_movements(tenant_id, type);
CREATE INDEX idx_stock_movements_tenant_created ON stock_movements(tenant_id, created_at DESC);
CREATE INDEX idx_stock_movements_reference ON stock_movements(reference_type, reference_id);
CREATE INDEX idx_stock_movement_items_movement ON stock_movement_items(stock_movement_id);
CREATE INDEX idx_stock_movement_items_product ON stock_movement_items(product_id);
```

**Step 2: Commit**

```bash
git add -A && git commit -m "feat(stock-movement): add Flyway migration V3 for stock_movements tables"
```

---

### Task 8: Integrate with TransferService

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/transfer/TransferService.java` (inject StockMovementService, create TRANSFER_OUT on execute)
- Modify: `src/main/java/br/com/stockshift/service/transfer/TransferValidationService.java` (create TRANSFER_IN on completeValidation)

**Step 1: Modify TransferService.execute()**

In `src/main/java/br/com/stockshift/service/transfer/TransferService.java`:

1. Add field: `private final StockMovementService stockMovementService;` (add import for `br.com.stockshift.service.stockmovement.StockMovementService` and `br.com.stockshift.model.entity.StockMovementItem`)
2. At the end of the `execute()` method, BEFORE `return transferMapper.toResponse(...)`, add:

```java
        // Create TRANSFER_OUT stock movement
        List<StockMovementItem> movementItems = transfer.getItems().stream()
                .map(ti -> StockMovementItem.builder()
                        .productId(ti.getProductId())
                        .productName(ti.getProductName())
                        .productSku(ti.getProductSku())
                        .batchId(ti.getSourceBatchId())
                        .batchCode(batchRepository.findById(ti.getSourceBatchId())
                                .map(Batch::getBatchCode).orElse("Unknown"))
                        .quantity(ti.getQuantitySent())
                        .build())
                .collect(Collectors.toList());

        stockMovementService.createForTransfer(
                tenantId, transfer.getSourceWarehouseId(), userId,
                StockMovementType.TRANSFER_OUT, transfer.getId(),
                movementItems, "Transfer " + saved.getCode() + " to " + destinationWarehouse.getName());
```

Add necessary imports:
```java
import br.com.stockshift.service.stockmovement.StockMovementService;
import br.com.stockshift.model.enums.StockMovementType;
import java.util.stream.Collectors;
```

**Step 2: Modify TransferValidationService.completeValidation()**

In `src/main/java/br/com/stockshift/service/transfer/TransferValidationService.java`:

1. Add field: `private final StockMovementService stockMovementService;` (add import)
2. At the end of `completeValidation()`, BEFORE `return CompleteValidationResponse.builder()...`, add:

```java
        // Create TRANSFER_IN stock movement
        List<StockMovementItem> movementItems = transfer.getItems().stream()
                .filter(ti -> ti.getQuantityReceived().compareTo(BigDecimal.ZERO) > 0)
                .map(ti -> StockMovementItem.builder()
                        .productId(ti.getProductId())
                        .productName(ti.getProductName())
                        .productSku(ti.getProductSku())
                        .batchId(ti.getDestinationBatchId())
                        .batchCode(batchRepository.findById(ti.getDestinationBatchId())
                                .map(Batch::getBatchCode).orElse("Unknown"))
                        .quantity(ti.getQuantityReceived())
                        .build())
                .collect(Collectors.toList());

        stockMovementService.createForTransfer(
                tenantId, transfer.getDestinationWarehouseId(), userId,
                StockMovementType.TRANSFER_IN, transfer.getId(),
                movementItems, "Transfer " + transfer.getCode() + " from " + sourceWarehouse.getName());
```

Add necessary imports:
```java
import br.com.stockshift.service.stockmovement.StockMovementService;
import br.com.stockshift.model.entity.StockMovementItem;
import br.com.stockshift.model.enums.StockMovementType;
import java.util.stream.Collectors;
```

**Step 3: Compile check**

Run: `cd /home/lexmarcos/projects/stockshift-backend && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Run existing tests**

Run: `cd /home/lexmarcos/projects/stockshift-backend && ./gradlew test 2>&1 | tail -20`
Expected: All existing tests should still pass (TransferService now has additional constructor parameter injected by Spring)

**Step 5: Commit**

```bash
git add -A && git commit -m "feat(stock-movement): integrate StockMovement with Transfer execute and validation"
```

---

## Summary of all changes

| # | Task | New files | Modified files |
|---|------|-----------|----------------|
| 1 | Enums | 2 | 1 (LedgerEntryType) |
| 2 | Entities | 2 | 0 |
| 3 | DTOs | 5 | 0 |
| 4 | Repositories + Mapper | 3 | 1 (BatchRepository) |
| 5 | Service | 1 | 0 |
| 6 | Controller | 1 | 1 (PermissionResource) |
| 7 | Migration | 1 | 0 |
| 8 | Transfer integration | 0 | 2 (TransferService, TransferValidationService) |
| **Total** | | **15** | **5** |
