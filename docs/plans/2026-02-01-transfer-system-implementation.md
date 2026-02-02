# Transfer System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement warehouse-to-warehouse transfer system with state machine, barcode validation, and full audit trail.

**Architecture:** State machine pattern for transfer lifecycle. Transfer entity holds metadata, TransferItem holds product snapshots and quantities, TransferValidationLog tracks barcode scans. All stock movements recorded in InventoryLedger.

**Tech Stack:** Spring Boot 3, JPA/Hibernate, PostgreSQL, Flyway migrations, Lombok, Jakarta Validation.

---

## Task 1: Database Migrations

**Files:**
- Create: `src/main/resources/db/migration/V3__create_transfer_tables.sql`
- Create: `src/main/resources/db/migration/V4__add_transit_quantity_to_batch.sql`

**Step 1: Create transfer tables migration**

```sql
-- V3__create_transfer_tables.sql

CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    source_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    destination_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    notes TEXT,
    created_by_user_id UUID NOT NULL,
    executed_by_user_id UUID,
    executed_at TIMESTAMP,
    validated_by_user_id UUID,
    validated_at TIMESTAMP,
    cancelled_by_user_id UUID,
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_transfers_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_transfers_different_warehouses CHECK (source_warehouse_id != destination_warehouse_id)
);

CREATE INDEX idx_transfers_tenant ON transfers(tenant_id);
CREATE INDEX idx_transfers_source_warehouse ON transfers(source_warehouse_id);
CREATE INDEX idx_transfers_destination_warehouse ON transfers(destination_warehouse_id);
CREATE INDEX idx_transfers_status ON transfers(status);

CREATE TABLE transfer_items (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    source_batch_id UUID NOT NULL REFERENCES batches(id),
    product_id UUID NOT NULL,
    product_barcode VARCHAR(255),
    product_name VARCHAR(255) NOT NULL,
    product_sku VARCHAR(255),
    quantity_sent NUMERIC(19, 4) NOT NULL,
    quantity_received NUMERIC(19, 4) NOT NULL DEFAULT 0,
    destination_batch_id UUID REFERENCES batches(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transfer_items_transfer ON transfer_items(transfer_id);
CREATE INDEX idx_transfer_items_source_batch ON transfer_items(source_batch_id);
CREATE INDEX idx_transfer_items_product_barcode ON transfer_items(product_barcode);

CREATE TABLE transfer_validation_logs (
    id UUID PRIMARY KEY,
    transfer_item_id UUID REFERENCES transfer_items(id) ON DELETE CASCADE,
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    barcode VARCHAR(255) NOT NULL,
    validated_by_user_id UUID NOT NULL,
    validated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    valid BOOLEAN NOT NULL
);

CREATE INDEX idx_transfer_validation_logs_transfer ON transfer_validation_logs(transfer_id);
CREATE INDEX idx_transfer_validation_logs_item ON transfer_validation_logs(transfer_item_id);
```

**Step 2: Create transit quantity migration**

```sql
-- V4__add_transit_quantity_to_batch.sql

ALTER TABLE batches ADD COLUMN transit_quantity NUMERIC(19, 4) NOT NULL DEFAULT 0;

-- Add constraint to ensure transit_quantity is not negative
ALTER TABLE batches ADD CONSTRAINT chk_batches_transit_quantity_non_negative CHECK (transit_quantity >= 0);
```

**Step 3: Run migrations to verify**

Run: `./gradlew flywayMigrate` or start the application
Expected: Migrations V3 and V4 applied successfully

**Step 4: Commit**

```bash
git add src/main/resources/db/migration/V3__create_transfer_tables.sql src/main/resources/db/migration/V4__add_transit_quantity_to_batch.sql
git commit -m "feat(db): add transfer tables and transit_quantity column"
```

---

## Task 2: TransferStatus Enum

**Files:**
- Create: `src/main/java/br/com/stockshift/model/enums/TransferStatus.java`

**Step 1: Create the enum**

```java
package br.com.stockshift.model.enums;

public enum TransferStatus {
    DRAFT,
    IN_TRANSIT,
    PENDING_VALIDATION,
    COMPLETED,
    COMPLETED_WITH_DISCREPANCY,
    CANCELLED
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/model/enums/TransferStatus.java
git commit -m "feat(entity): add TransferStatus enum"
```

---

## Task 3: Transfer Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/Transfer.java`

**Step 1: Create Transfer entity**

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transfers")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transfer extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/Transfer.java
git commit -m "feat(entity): add Transfer entity"
```

---

## Task 4: TransferItem Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/TransferItem.java`

**Step 1: Create TransferItem entity**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Transfer transfer;

    @Column(name = "source_batch_id", nullable = false)
    private UUID sourceBatchId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_barcode")
    private String productBarcode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_sku")
    private String productSku;

    @Column(name = "quantity_sent", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantitySent;

    @Column(name = "quantity_received", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal quantityReceived = BigDecimal.ZERO;

    @Column(name = "destination_batch_id")
    private UUID destinationBatchId;

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

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/TransferItem.java
git commit -m "feat(entity): add TransferItem entity"
```

---

## Task 5: TransferValidationLog Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/TransferValidationLog.java`

**Step 1: Create TransferValidationLog entity**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_validation_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferValidationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transfer_item_id")
    private UUID transferItemId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(nullable = false)
    private String barcode;

    @Column(name = "validated_by_user_id", nullable = false)
    private UUID validatedByUserId;

    @Column(name = "validated_at", nullable = false)
    @Builder.Default
    private Instant validatedAt = Instant.now();

    @Column(nullable = false)
    private Boolean valid;
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/TransferValidationLog.java
git commit -m "feat(entity): add TransferValidationLog entity"
```

---

## Task 6: Update Batch Entity

**Files:**
- Modify: `src/main/java/br/com/stockshift/model/entity/Batch.java`

**Step 1: Add transitQuantity field to Batch entity**

Add this field to the Batch class:

```java
@Column(name = "transit_quantity", nullable = false, precision = 19, scale = 4)
@Builder.Default
private BigDecimal transitQuantity = BigDecimal.ZERO;
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/Batch.java
git commit -m "feat(entity): add transitQuantity field to Batch"
```

---

## Task 7: Update LedgerEntryType Enum

**Files:**
- Modify: `src/main/java/br/com/stockshift/model/enums/LedgerEntryType.java`

**Step 1: Add new transfer-related entry types**

Add these values to the LedgerEntryType enum:

```java
TRANSFER_OUT,
TRANSFER_CANCELLED,
TRANSFER_IN,
TRANSFER_IN_DISCREPANCY
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/model/enums/LedgerEntryType.java
git commit -m "feat(entity): add transfer entry types to LedgerEntryType"
```

---

## Task 8: Repositories

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/TransferRepository.java`
- Create: `src/main/java/br/com/stockshift/repository/TransferItemRepository.java`
- Create: `src/main/java/br/com/stockshift/repository/TransferValidationLogRepository.java`

**Step 1: Create TransferRepository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.enums.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    @Query("SELECT t FROM Transfer t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<Transfer> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM Transfer t WHERE t.tenantId = :tenantId")
    Page<Transfer> findAllByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT t FROM Transfer t WHERE t.tenantId = :tenantId AND t.status = :status")
    Page<Transfer> findAllByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") TransferStatus status, Pageable pageable);

    @Query("SELECT t FROM Transfer t WHERE t.tenantId = :tenantId AND t.sourceWarehouseId = :warehouseId")
    Page<Transfer> findAllByTenantIdAndSourceWarehouseId(@Param("tenantId") UUID tenantId, @Param("warehouseId") UUID warehouseId, Pageable pageable);

    @Query("SELECT t FROM Transfer t WHERE t.tenantId = :tenantId AND t.destinationWarehouseId = :warehouseId")
    Page<Transfer> findAllByTenantIdAndDestinationWarehouseId(@Param("tenantId") UUID tenantId, @Param("warehouseId") UUID warehouseId, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Transfer t WHERE t.tenantId = :tenantId AND t.code LIKE :prefix%")
    long countByTenantIdAndCodePrefix(@Param("tenantId") UUID tenantId, @Param("prefix") String prefix);
}
```

**Step 2: Create TransferItemRepository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferItemRepository extends JpaRepository<TransferItem, UUID> {

    List<TransferItem> findAllByTransferId(UUID transferId);

    @Query("SELECT ti FROM TransferItem ti WHERE ti.transfer.id = :transferId AND ti.productBarcode = :barcode")
    Optional<TransferItem> findByTransferIdAndProductBarcode(@Param("transferId") UUID transferId, @Param("barcode") String barcode);
}
```

**Step 3: Create TransferValidationLogRepository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferValidationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferValidationLogRepository extends JpaRepository<TransferValidationLog, UUID> {

    List<TransferValidationLog> findAllByTransferId(UUID transferId);

    List<TransferValidationLog> findAllByTransferItemId(UUID transferItemId);
}
```

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/TransferRepository.java src/main/java/br/com/stockshift/repository/TransferItemRepository.java src/main/java/br/com/stockshift/repository/TransferValidationLogRepository.java
git commit -m "feat(repository): add transfer repositories"
```

---

## Task 9: DTOs - Request Classes

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/transfer/CreateTransferRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/CreateTransferItemRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/UpdateTransferRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/ScanBarcodeRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/CancelTransferRequest.java`

**Step 1: Create CreateTransferRequest**

```java
package br.com.stockshift.dto.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransferRequest {

    @NotNull(message = "Destination warehouse ID is required")
    private UUID destinationWarehouseId;

    private String notes;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<CreateTransferItemRequest> items;
}
```

**Step 2: Create CreateTransferItemRequest**

```java
package br.com.stockshift.dto.transfer;

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
public class CreateTransferItemRequest {

    @NotNull(message = "Source batch ID is required")
    private UUID sourceBatchId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;
}
```

**Step 3: Create UpdateTransferRequest**

```java
package br.com.stockshift.dto.transfer;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTransferRequest {

    private String notes;

    @Valid
    private List<CreateTransferItemRequest> items;
}
```

**Step 4: Create ScanBarcodeRequest**

```java
package br.com.stockshift.dto.transfer;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanBarcodeRequest {

    @NotBlank(message = "Barcode is required")
    private String barcode;
}
```

**Step 5: Create CancelTransferRequest**

```java
package br.com.stockshift.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelTransferRequest {

    private String reason;
}
```

**Step 6: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/transfer/
git commit -m "feat(dto): add transfer request DTOs"
```

---

## Task 10: DTOs - Response Classes

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/transfer/TransferResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/TransferItemResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/ScanBarcodeResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/CompleteValidationResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/DiscrepancyReportResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/ValidationLogResponse.java`

**Step 1: Create TransferResponse**

```java
package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferStatus;
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
public class TransferResponse {

    private UUID id;
    private String code;
    private UUID sourceWarehouseId;
    private String sourceWarehouseName;
    private UUID destinationWarehouseId;
    private String destinationWarehouseName;
    private TransferStatus status;
    private String notes;
    private UUID createdByUserId;
    private UUID executedByUserId;
    private Instant executedAt;
    private UUID validatedByUserId;
    private Instant validatedAt;
    private UUID cancelledByUserId;
    private Instant cancelledAt;
    private String cancellationReason;
    private Instant createdAt;
    private Instant updatedAt;
    private List<TransferItemResponse> items;
}
```

**Step 2: Create TransferItemResponse**

```java
package br.com.stockshift.dto.transfer;

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
public class TransferItemResponse {

    private UUID id;
    private UUID sourceBatchId;
    private UUID productId;
    private String productBarcode;
    private String productName;
    private String productSku;
    private BigDecimal quantitySent;
    private BigDecimal quantityReceived;
    private UUID destinationBatchId;
}
```

**Step 3: Create ScanBarcodeResponse**

```java
package br.com.stockshift.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanBarcodeResponse {

    private boolean valid;
    private String message;
    private String warning;
    private String productName;
    private String productBarcode;
    private BigDecimal quantitySent;
    private BigDecimal quantityReceived;
}
```

**Step 4: Create CompleteValidationResponse**

```java
package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteValidationResponse {

    private UUID transferId;
    private TransferStatus status;
    private ValidationSummary summary;
    private List<DiscrepancyItem> discrepancies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationSummary {
        private int totalItemTypes;
        private int itemsOk;
        private int itemsWithDiscrepancy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscrepancyItem {
        private String productName;
        private String productBarcode;
        private java.math.BigDecimal quantitySent;
        private java.math.BigDecimal quantityReceived;
        private java.math.BigDecimal difference;
        private DiscrepancyType type;
    }

    public enum DiscrepancyType {
        SHORTAGE,
        OVERAGE
    }
}
```

**Step 5: Create DiscrepancyReportResponse**

```java
package br.com.stockshift.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyReportResponse {

    private UUID transferId;
    private String transferCode;
    private String sourceWarehouseName;
    private String destinationWarehouseName;
    private Instant completedAt;
    private List<CompleteValidationResponse.DiscrepancyItem> discrepancies;
    private BigDecimal totalShortage;
    private BigDecimal totalOverage;
}
```

**Step 6: Create ValidationLogResponse**

```java
package br.com.stockshift.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationLogResponse {

    private UUID id;
    private UUID transferItemId;
    private String barcode;
    private UUID validatedByUserId;
    private Instant validatedAt;
    private boolean valid;
}
```

**Step 7: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/transfer/
git commit -m "feat(dto): add transfer response DTOs"
```

---

## Task 11: TransferMapper

**Files:**
- Create: `src/main/java/br/com/stockshift/mapper/TransferMapper.java`

**Step 1: Create TransferMapper**

```java
package br.com.stockshift.mapper;

import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferItem;
import br.com.stockshift.model.entity.TransferValidationLog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TransferMapper {

    public TransferResponse toResponse(Transfer transfer, String sourceWarehouseName, String destinationWarehouseName) {
        return TransferResponse.builder()
                .id(transfer.getId())
                .code(transfer.getCode())
                .sourceWarehouseId(transfer.getSourceWarehouseId())
                .sourceWarehouseName(sourceWarehouseName)
                .destinationWarehouseId(transfer.getDestinationWarehouseId())
                .destinationWarehouseName(destinationWarehouseName)
                .status(transfer.getStatus())
                .notes(transfer.getNotes())
                .createdByUserId(transfer.getCreatedByUserId())
                .executedByUserId(transfer.getExecutedByUserId())
                .executedAt(transfer.getExecutedAt())
                .validatedByUserId(transfer.getValidatedByUserId())
                .validatedAt(transfer.getValidatedAt())
                .cancelledByUserId(transfer.getCancelledByUserId())
                .cancelledAt(transfer.getCancelledAt())
                .cancellationReason(transfer.getCancellationReason())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .items(toItemResponseList(transfer.getItems()))
                .build();
    }

    public TransferItemResponse toItemResponse(TransferItem item) {
        return TransferItemResponse.builder()
                .id(item.getId())
                .sourceBatchId(item.getSourceBatchId())
                .productId(item.getProductId())
                .productBarcode(item.getProductBarcode())
                .productName(item.getProductName())
                .productSku(item.getProductSku())
                .quantitySent(item.getQuantitySent())
                .quantityReceived(item.getQuantityReceived())
                .destinationBatchId(item.getDestinationBatchId())
                .build();
    }

    public List<TransferItemResponse> toItemResponseList(List<TransferItem> items) {
        return items.stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());
    }

    public ValidationLogResponse toValidationLogResponse(TransferValidationLog log) {
        return ValidationLogResponse.builder()
                .id(log.getId())
                .transferItemId(log.getTransferItemId())
                .barcode(log.getBarcode())
                .validatedByUserId(log.getValidatedByUserId())
                .validatedAt(log.getValidatedAt())
                .valid(log.getValid())
                .build();
    }

    public List<ValidationLogResponse> toValidationLogResponseList(List<TransferValidationLog> logs) {
        return logs.stream()
                .map(this::toValidationLogResponse)
                .collect(Collectors.toList());
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/mapper/TransferMapper.java
git commit -m "feat(mapper): add TransferMapper"
```

---

## Task 12: TransferStateMachine

**Files:**
- Create: `src/main/java/br/com/stockshift/service/transfer/TransferStateMachine.java`

**Step 1: Create TransferStateMachine**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.model.enums.TransferStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class TransferStateMachine {

    private static final Map<TransferStatus, Set<TransferStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(TransferStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(TransferStatus.DRAFT, Set.of(
                TransferStatus.IN_TRANSIT,
                TransferStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(TransferStatus.IN_TRANSIT, Set.of(
                TransferStatus.PENDING_VALIDATION,
                TransferStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(TransferStatus.PENDING_VALIDATION, Set.of(
                TransferStatus.COMPLETED,
                TransferStatus.COMPLETED_WITH_DISCREPANCY
        ));
        ALLOWED_TRANSITIONS.put(TransferStatus.COMPLETED, Set.of());
        ALLOWED_TRANSITIONS.put(TransferStatus.COMPLETED_WITH_DISCREPANCY, Set.of());
        ALLOWED_TRANSITIONS.put(TransferStatus.CANCELLED, Set.of());
    }

    public boolean canTransition(TransferStatus from, TransferStatus to) {
        Set<TransferStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public void validateTransition(TransferStatus from, TransferStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    String.format("Cannot transition from %s to %s", from, to)
            );
        }
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferStateMachine.java
git commit -m "feat(service): add TransferStateMachine"
```

---

## Task 13: TransferService - Core Operations

**Files:**
- Create: `src/main/java/br/com/stockshift/service/transfer/TransferService.java`

**Step 1: Create TransferService with create, get, list, update operations**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.config.TenantContext;
import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.mapper.TransferMapper;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.LedgerEntryType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TransferRepository transferRepository;
    private final TransferItemRepository transferItemRepository;
    private final TransferValidationLogRepository validationLogRepository;
    private final BatchRepository batchRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryLedgerRepository ledgerRepository;
    private final TransferMapper transferMapper;
    private final TransferStateMachine stateMachine;
    private final SecurityUtils securityUtils;

    @Transactional
    public TransferResponse create(CreateTransferRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID sourceWarehouseId = securityUtils.getCurrentWarehouseId();
        UUID userId = securityUtils.getCurrentUserId();

        // Validate destination warehouse
        if (sourceWarehouseId.equals(request.getDestinationWarehouseId())) {
            throw new BadRequestException("Source and destination warehouses must be different");
        }

        Warehouse destinationWarehouse = warehouseRepository.findByTenantIdAndId(tenantId, request.getDestinationWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination warehouse not found"));

        Warehouse sourceWarehouse = warehouseRepository.findByTenantIdAndId(tenantId, sourceWarehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Source warehouse not found"));

        // Generate transfer code
        String code = generateTransferCode(tenantId);

        // Create transfer
        Transfer transfer = Transfer.builder()
                .tenantId(tenantId)
                .code(code)
                .sourceWarehouseId(sourceWarehouseId)
                .destinationWarehouseId(request.getDestinationWarehouseId())
                .status(TransferStatus.DRAFT)
                .notes(request.getNotes())
                .createdByUserId(userId)
                .build();

        // Add items
        for (CreateTransferItemRequest itemRequest : request.getItems()) {
            TransferItem item = createTransferItem(tenantId, sourceWarehouseId, itemRequest);
            transfer.addItem(item);
        }

        Transfer saved = transferRepository.save(transfer);
        log.info("Transfer {} created by user {}", saved.getCode(), userId);

        return transferMapper.toResponse(saved, sourceWarehouse.getName(), destinationWarehouse.getName());
    }

    private TransferItem createTransferItem(UUID tenantId, UUID sourceWarehouseId, CreateTransferItemRequest request) {
        Batch batch = batchRepository.findByTenantIdAndId(tenantId, request.getSourceBatchId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + request.getSourceBatchId()));

        if (!batch.getWarehouse().getId().equals(sourceWarehouseId)) {
            throw new BadRequestException("Batch does not belong to source warehouse");
        }

        if (batch.getQuantity().compareTo(request.getQuantity()) < 0) {
            throw new BadRequestException("Insufficient quantity in batch " + batch.getBatchCode());
        }

        Product product = batch.getProduct();

        return TransferItem.builder()
                .sourceBatchId(batch.getId())
                .productId(product.getId())
                .productBarcode(product.getBarcode())
                .productName(product.getName())
                .productSku(product.getSku())
                .quantitySent(request.getQuantity())
                .quantityReceived(BigDecimal.ZERO)
                .build();
    }

    private String generateTransferCode(UUID tenantId) {
        String prefix = "TRF-" + LocalDate.now().getYear() + "-";
        long count = transferRepository.countByTenantIdAndCodePrefix(tenantId, prefix);
        return prefix + String.format("%04d", count + 1);
    }

    @Transactional(readOnly = true)
    public TransferResponse getById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        String sourceWarehouseName = warehouseRepository.findById(transfer.getSourceWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");
        String destinationWarehouseName = warehouseRepository.findById(transfer.getDestinationWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");

        return transferMapper.toResponse(transfer, sourceWarehouseName, destinationWarehouseName);
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> list(TransferStatus status, UUID sourceWarehouseId, UUID destinationWarehouseId, Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();

        Page<Transfer> transfers;
        if (status != null) {
            transfers = transferRepository.findAllByTenantIdAndStatus(tenantId, status, pageable);
        } else if (sourceWarehouseId != null) {
            transfers = transferRepository.findAllByTenantIdAndSourceWarehouseId(tenantId, sourceWarehouseId, pageable);
        } else if (destinationWarehouseId != null) {
            transfers = transferRepository.findAllByTenantIdAndDestinationWarehouseId(tenantId, destinationWarehouseId, pageable);
        } else {
            transfers = transferRepository.findAllByTenantId(tenantId, pageable);
        }

        return transfers.map(t -> {
            String sourceName = warehouseRepository.findById(t.getSourceWarehouseId())
                    .map(Warehouse::getName).orElse("Unknown");
            String destName = warehouseRepository.findById(t.getDestinationWarehouseId())
                    .map(Warehouse::getName).orElse("Unknown");
            return transferMapper.toResponse(t, sourceName, destName);
        });
    }

    @Transactional
    public TransferResponse update(UUID id, UpdateTransferRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        validateSourceWarehouseAccess(transfer, currentWarehouseId);

        if (transfer.getStatus() != TransferStatus.DRAFT) {
            throw new BadRequestException("Can only update transfers in DRAFT status");
        }

        if (request.getNotes() != null) {
            transfer.setNotes(request.getNotes());
        }

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            transfer.getItems().clear();
            for (CreateTransferItemRequest itemRequest : request.getItems()) {
                TransferItem item = createTransferItem(tenantId, currentWarehouseId, itemRequest);
                transfer.addItem(item);
            }
        }

        Transfer saved = transferRepository.save(transfer);

        String sourceWarehouseName = warehouseRepository.findById(transfer.getSourceWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");
        String destinationWarehouseName = warehouseRepository.findById(transfer.getDestinationWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");

        return transferMapper.toResponse(saved, sourceWarehouseName, destinationWarehouseName);
    }

    private void validateSourceWarehouseAccess(Transfer transfer, UUID currentWarehouseId) {
        if (!transfer.getSourceWarehouseId().equals(currentWarehouseId)) {
            throw new ForbiddenException("Only source warehouse can perform this action");
        }
    }

    private void validateDestinationWarehouseAccess(Transfer transfer, UUID currentWarehouseId) {
        if (!transfer.getDestinationWarehouseId().equals(currentWarehouseId)) {
            throw new ForbiddenException("Only destination warehouse can perform this action");
        }
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferService.java
git commit -m "feat(service): add TransferService with CRUD operations"
```

---

## Task 14: TransferService - State Transitions (Execute, Cancel)

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/transfer/TransferService.java`

**Step 1: Add execute method to TransferService**

```java
@Transactional
public TransferResponse execute(UUID id) {
    UUID tenantId = TenantContext.getTenantId();
    UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();
    UUID userId = securityUtils.getCurrentUserId();

    Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

    validateSourceWarehouseAccess(transfer, currentWarehouseId);
    stateMachine.validateTransition(transfer.getStatus(), TransferStatus.IN_TRANSIT);

    Warehouse destinationWarehouse = warehouseRepository.findById(transfer.getDestinationWarehouseId())
            .orElseThrow(() -> new ResourceNotFoundException("Destination warehouse not found"));

    // Process each item: validate stock, update batch quantities, create ledger entries
    for (TransferItem item : transfer.getItems()) {
        Batch batch = batchRepository.findByIdForUpdate(item.getSourceBatchId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + item.getSourceBatchId()));

        if (batch.getQuantity().compareTo(item.getQuantitySent()) < 0) {
            throw new BadRequestException("Insufficient quantity in batch " + batch.getBatchCode() +
                    ". Available: " + batch.getQuantity() + ", Required: " + item.getQuantitySent());
        }

        // Update batch quantities
        batch.setQuantity(batch.getQuantity().subtract(item.getQuantitySent()));
        batch.setTransitQuantity(batch.getTransitQuantity().add(item.getQuantitySent()));
        batchRepository.save(batch);

        // Create ledger entry
        InventoryLedger ledgerEntry = InventoryLedger.builder()
                .tenantId(tenantId)
                .warehouseId(transfer.getSourceWarehouseId())
                .batchId(batch.getId())
                .productId(item.getProductId())
                .type(LedgerEntryType.TRANSFER_OUT)
                .quantity(item.getQuantitySent().negate())
                .referenceType("TRANSFER")
                .referenceId(transfer.getId())
                .notes("Transfer to " + destinationWarehouse.getName())
                .build();
        ledgerRepository.save(ledgerEntry);
    }

    // Update transfer status
    transfer.setStatus(TransferStatus.IN_TRANSIT);
    transfer.setExecutedByUserId(userId);
    transfer.setExecutedAt(Instant.now());

    Transfer saved = transferRepository.save(transfer);
    log.info("Transfer {} executed by user {}", saved.getCode(), userId);

    String sourceWarehouseName = warehouseRepository.findById(transfer.getSourceWarehouseId())
            .map(Warehouse::getName).orElse("Unknown");

    return transferMapper.toResponse(saved, sourceWarehouseName, destinationWarehouse.getName());
}
```

**Step 2: Add cancel method to TransferService**

```java
@Transactional
public TransferResponse cancel(UUID id, CancelTransferRequest request) {
    UUID tenantId = TenantContext.getTenantId();
    UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();
    UUID userId = securityUtils.getCurrentUserId();

    Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

    validateSourceWarehouseAccess(transfer, currentWarehouseId);
    stateMachine.validateTransition(transfer.getStatus(), TransferStatus.CANCELLED);

    if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException("Cancellation reason is required for in-transit transfers");
        }

        // Revert stock movements
        for (TransferItem item : transfer.getItems()) {
            Batch batch = batchRepository.findByIdForUpdate(item.getSourceBatchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found"));

            batch.setTransitQuantity(batch.getTransitQuantity().subtract(item.getQuantitySent()));
            batch.setQuantity(batch.getQuantity().add(item.getQuantitySent()));
            batchRepository.save(batch);

            // Create reversal ledger entry
            InventoryLedger ledgerEntry = InventoryLedger.builder()
                    .tenantId(tenantId)
                    .warehouseId(transfer.getSourceWarehouseId())
                    .batchId(batch.getId())
                    .productId(item.getProductId())
                    .type(LedgerEntryType.TRANSFER_CANCELLED)
                    .quantity(item.getQuantitySent())
                    .referenceType("TRANSFER")
                    .referenceId(transfer.getId())
                    .notes("Transfer cancelled: " + request.getReason())
                    .build();
            ledgerRepository.save(ledgerEntry);
        }
    }

    transfer.setStatus(TransferStatus.CANCELLED);
    transfer.setCancelledByUserId(userId);
    transfer.setCancelledAt(Instant.now());
    transfer.setCancellationReason(request.getReason());

    Transfer saved = transferRepository.save(transfer);
    log.info("Transfer {} cancelled by user {}", saved.getCode(), userId);

    String sourceWarehouseName = warehouseRepository.findById(transfer.getSourceWarehouseId())
            .map(Warehouse::getName).orElse("Unknown");
    String destinationWarehouseName = warehouseRepository.findById(transfer.getDestinationWarehouseId())
            .map(Warehouse::getName).orElse("Unknown");

    return transferMapper.toResponse(saved, sourceWarehouseName, destinationWarehouseName);
}
```

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferService.java
git commit -m "feat(service): add execute and cancel operations to TransferService"
```

---

## Task 15: TransferValidationService

**Files:**
- Create: `src/main/java/br/com/stockshift/service/transfer/TransferValidationService.java`

**Step 1: Create TransferValidationService**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.config.TenantContext;
import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.mapper.TransferMapper;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.LedgerEntryType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferValidationService {

    private final TransferRepository transferRepository;
    private final TransferItemRepository transferItemRepository;
    private final TransferValidationLogRepository validationLogRepository;
    private final BatchRepository batchRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryLedgerRepository ledgerRepository;
    private final TransferMapper transferMapper;
    private final TransferStateMachine stateMachine;
    private final SecurityUtils securityUtils;

    @Transactional
    public TransferResponse startValidation(UUID transferId) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        validateDestinationWarehouseAccess(transfer, currentWarehouseId);
        stateMachine.validateTransition(transfer.getStatus(), TransferStatus.PENDING_VALIDATION);

        transfer.setStatus(TransferStatus.PENDING_VALIDATION);
        Transfer saved = transferRepository.save(transfer);

        log.info("Transfer {} validation started", saved.getCode());

        String sourceWarehouseName = warehouseRepository.findById(transfer.getSourceWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");
        String destinationWarehouseName = warehouseRepository.findById(transfer.getDestinationWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");

        return transferMapper.toResponse(saved, sourceWarehouseName, destinationWarehouseName);
    }

    @Transactional
    public ScanBarcodeResponse scanBarcode(UUID transferId, ScanBarcodeRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();
        UUID userId = securityUtils.getCurrentUserId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        validateDestinationWarehouseAccess(transfer, currentWarehouseId);

        if (transfer.getStatus() != TransferStatus.PENDING_VALIDATION) {
            throw new BadRequestException("Transfer must be in PENDING_VALIDATION status to scan");
        }

        String barcode = request.getBarcode().trim();

        // Find matching item
        TransferItem matchingItem = transferItemRepository.findByTransferIdAndProductBarcode(transferId, barcode)
                .orElse(null);

        // Create validation log
        TransferValidationLog logEntry = TransferValidationLog.builder()
                .transferId(transferId)
                .transferItemId(matchingItem != null ? matchingItem.getId() : null)
                .barcode(barcode)
                .validatedByUserId(userId)
                .validatedAt(Instant.now())
                .valid(matchingItem != null)
                .build();
        validationLogRepository.save(logEntry);

        if (matchingItem == null) {
            log.warn("Invalid barcode {} scanned for transfer {}", barcode, transfer.getCode());
            return ScanBarcodeResponse.builder()
                    .valid(false)
                    .message("Product does not belong to this transfer")
                    .build();
        }

        // Increment quantity received
        matchingItem.setQuantityReceived(matchingItem.getQuantityReceived().add(BigDecimal.ONE));
        transferItemRepository.save(matchingItem);

        ScanBarcodeResponse.ScanBarcodeResponseBuilder responseBuilder = ScanBarcodeResponse.builder()
                .valid(true)
                .message("Product registered")
                .productName(matchingItem.getProductName())
                .productBarcode(matchingItem.getProductBarcode())
                .quantitySent(matchingItem.getQuantitySent())
                .quantityReceived(matchingItem.getQuantityReceived());

        if (matchingItem.getQuantityReceived().compareTo(matchingItem.getQuantitySent()) > 0) {
            responseBuilder.warning("Quantity received exceeds quantity sent");
        }

        return responseBuilder.build();
    }

    @Transactional
    public CompleteValidationResponse completeValidation(UUID transferId) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();
        UUID userId = securityUtils.getCurrentUserId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        validateDestinationWarehouseAccess(transfer, currentWarehouseId);

        if (transfer.getStatus() != TransferStatus.PENDING_VALIDATION) {
            throw new BadRequestException("Transfer must be in PENDING_VALIDATION status to complete");
        }

        Warehouse sourceWarehouse = warehouseRepository.findById(transfer.getSourceWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Source warehouse not found"));

        List<CompleteValidationResponse.DiscrepancyItem> discrepancies = new ArrayList<>();
        int itemsOk = 0;
        int itemsWithDiscrepancy = 0;

        for (TransferItem item : transfer.getItems()) {
            // Clear transit quantity from source batch
            Batch sourceBatch = batchRepository.findByIdForUpdate(item.getSourceBatchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source batch not found"));
            sourceBatch.setTransitQuantity(sourceBatch.getTransitQuantity().subtract(item.getQuantitySent()));
            batchRepository.save(sourceBatch);

            // Create destination batch if quantity received > 0
            if (item.getQuantityReceived().compareTo(BigDecimal.ZERO) > 0) {
                Batch destinationBatch = Batch.builder()
                        .tenantId(tenantId)
                        .product(sourceBatch.getProduct())
                        .warehouse(warehouseRepository.findById(transfer.getDestinationWarehouseId()).orElseThrow())
                        .batchCode(sourceBatch.getBatchCode() + "-TRF")
                        .quantity(item.getQuantityReceived())
                        .transitQuantity(BigDecimal.ZERO)
                        .costPrice(sourceBatch.getCostPrice())
                        .sellingPrice(sourceBatch.getSellingPrice())
                        .manufacturedDate(sourceBatch.getManufacturedDate())
                        .expirationDate(sourceBatch.getExpirationDate())
                        .originBatchId(sourceBatch.getId())
                        .build();
                Batch savedBatch = batchRepository.save(destinationBatch);
                item.setDestinationBatchId(savedBatch.getId());

                // Create ledger entry for destination
                LedgerEntryType entryType = item.getQuantityReceived().compareTo(item.getQuantitySent()) == 0
                        ? LedgerEntryType.TRANSFER_IN
                        : LedgerEntryType.TRANSFER_IN_DISCREPANCY;

                InventoryLedger ledgerEntry = InventoryLedger.builder()
                        .tenantId(tenantId)
                        .warehouseId(transfer.getDestinationWarehouseId())
                        .batchId(savedBatch.getId())
                        .productId(item.getProductId())
                        .type(entryType)
                        .quantity(item.getQuantityReceived())
                        .referenceType("TRANSFER")
                        .referenceId(transfer.getId())
                        .notes("Transfer from " + sourceWarehouse.getName())
                        .build();
                ledgerRepository.save(ledgerEntry);
            }

            // Check for discrepancy
            BigDecimal difference = item.getQuantityReceived().subtract(item.getQuantitySent());
            if (difference.compareTo(BigDecimal.ZERO) != 0) {
                itemsWithDiscrepancy++;
                discrepancies.add(CompleteValidationResponse.DiscrepancyItem.builder()
                        .productName(item.getProductName())
                        .productBarcode(item.getProductBarcode())
                        .quantitySent(item.getQuantitySent())
                        .quantityReceived(item.getQuantityReceived())
                        .difference(difference)
                        .type(difference.compareTo(BigDecimal.ZERO) < 0
                                ? CompleteValidationResponse.DiscrepancyType.SHORTAGE
                                : CompleteValidationResponse.DiscrepancyType.OVERAGE)
                        .build());
            } else {
                itemsOk++;
            }

            transferItemRepository.save(item);
        }

        // Update transfer status
        TransferStatus finalStatus = discrepancies.isEmpty()
                ? TransferStatus.COMPLETED
                : TransferStatus.COMPLETED_WITH_DISCREPANCY;

        transfer.setStatus(finalStatus);
        transfer.setValidatedByUserId(userId);
        transfer.setValidatedAt(Instant.now());
        transferRepository.save(transfer);

        log.info("Transfer {} completed with status {}", transfer.getCode(), finalStatus);

        return CompleteValidationResponse.builder()
                .transferId(transfer.getId())
                .status(finalStatus)
                .summary(CompleteValidationResponse.ValidationSummary.builder()
                        .totalItemTypes(transfer.getItems().size())
                        .itemsOk(itemsOk)
                        .itemsWithDiscrepancy(itemsWithDiscrepancy)
                        .build())
                .discrepancies(discrepancies)
                .build();
    }

    @Transactional(readOnly = true)
    public DiscrepancyReportResponse getDiscrepancyReport(UUID transferId) {
        UUID tenantId = TenantContext.getTenantId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        if (transfer.getStatus() != TransferStatus.COMPLETED_WITH_DISCREPANCY) {
            throw new BadRequestException("Discrepancy report only available for transfers with discrepancies");
        }

        String sourceWarehouseName = warehouseRepository.findById(transfer.getSourceWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");
        String destinationWarehouseName = warehouseRepository.findById(transfer.getDestinationWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");

        List<CompleteValidationResponse.DiscrepancyItem> discrepancies = new ArrayList<>();
        BigDecimal totalShortage = BigDecimal.ZERO;
        BigDecimal totalOverage = BigDecimal.ZERO;

        for (TransferItem item : transfer.getItems()) {
            BigDecimal difference = item.getQuantityReceived().subtract(item.getQuantitySent());
            if (difference.compareTo(BigDecimal.ZERO) != 0) {
                CompleteValidationResponse.DiscrepancyType type = difference.compareTo(BigDecimal.ZERO) < 0
                        ? CompleteValidationResponse.DiscrepancyType.SHORTAGE
                        : CompleteValidationResponse.DiscrepancyType.OVERAGE;

                discrepancies.add(CompleteValidationResponse.DiscrepancyItem.builder()
                        .productName(item.getProductName())
                        .productBarcode(item.getProductBarcode())
                        .quantitySent(item.getQuantitySent())
                        .quantityReceived(item.getQuantityReceived())
                        .difference(difference)
                        .type(type)
                        .build());

                if (type == CompleteValidationResponse.DiscrepancyType.SHORTAGE) {
                    totalShortage = totalShortage.add(difference.abs());
                } else {
                    totalOverage = totalOverage.add(difference);
                }
            }
        }

        return DiscrepancyReportResponse.builder()
                .transferId(transfer.getId())
                .transferCode(transfer.getCode())
                .sourceWarehouseName(sourceWarehouseName)
                .destinationWarehouseName(destinationWarehouseName)
                .completedAt(transfer.getValidatedAt())
                .discrepancies(discrepancies)
                .totalShortage(totalShortage)
                .totalOverage(totalOverage)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ValidationLogResponse> getValidationLogs(UUID transferId) {
        UUID tenantId = TenantContext.getTenantId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        List<TransferValidationLog> logs = validationLogRepository.findAllByTransferId(transferId);
        return transferMapper.toValidationLogResponseList(logs);
    }

    private void validateDestinationWarehouseAccess(Transfer transfer, UUID currentWarehouseId) {
        if (!transfer.getDestinationWarehouseId().equals(currentWarehouseId)) {
            throw new ForbiddenException("Only destination warehouse can perform this action");
        }
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferValidationService.java
git commit -m "feat(service): add TransferValidationService"
```

---

## Task 16: TransferController

**Files:**
- Create: `src/main/java/br/com/stockshift/controller/TransferController.java`

**Step 1: Create TransferController**

```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.service.transfer.TransferService;
import br.com.stockshift.service.transfer.TransferValidationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stockshift/transfers")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class TransferController {

    private final TransferService transferService;
    private final TransferValidationService validationService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('TRANSFER_EXECUTE', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TransferResponse>> create(@Valid @RequestBody CreateTransferRequest request) {
        TransferResponse response = transferService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transfer created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TransferResponse>>> list(
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) UUID sourceWarehouseId,
            @RequestParam(required = false) UUID destinationWarehouseId,
            Pageable pageable) {
        Page<TransferResponse> response = transferService.list(status, sourceWarehouseId, destinationWarehouseId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Transfers retrieved successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransferResponse>> getById(@PathVariable UUID id) {
        TransferResponse response = transferService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Transfer retrieved successfully", response));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TRANSFER_EXECUTE', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TransferResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransferRequest request) {
        TransferResponse response = transferService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Transfer updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TRANSFER_CANCEL', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TransferResponse>> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelTransferRequest request) {
        TransferResponse response = transferService.cancel(id, request != null ? request : new CancelTransferRequest());
        return ResponseEntity.ok(ApiResponse.success("Transfer cancelled successfully", response));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAnyAuthority('TRANSFER_EXECUTE', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TransferResponse>> execute(@PathVariable UUID id) {
        TransferResponse response = transferService.execute(id);
        return ResponseEntity.ok(ApiResponse.success("Transfer executed successfully", response));
    }

    @PostMapping("/{id}/start-validation")
    @PreAuthorize("hasAnyAuthority('TRANSFER_VALIDATE', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TransferResponse>> startValidation(@PathVariable UUID id) {
        TransferResponse response = validationService.startValidation(id);
        return ResponseEntity.ok(ApiResponse.success("Validation started successfully", response));
    }

    @PostMapping("/{id}/scan")
    @PreAuthorize("hasAnyAuthority('TRANSFER_VALIDATE', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ScanBarcodeResponse>> scanBarcode(
            @PathVariable UUID id,
            @Valid @RequestBody ScanBarcodeRequest request) {
        ScanBarcodeResponse response = validationService.scanBarcode(id, request);
        return ResponseEntity.ok(ApiResponse.success("Barcode processed", response));
    }

    @PostMapping("/{id}/complete-validation")
    @PreAuthorize("hasAnyAuthority('TRANSFER_VALIDATE', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CompleteValidationResponse>> completeValidation(@PathVariable UUID id) {
        CompleteValidationResponse response = validationService.completeValidation(id);
        return ResponseEntity.ok(ApiResponse.success("Validation completed successfully", response));
    }

    @GetMapping("/{id}/discrepancy-report")
    public ResponseEntity<ApiResponse<DiscrepancyReportResponse>> getDiscrepancyReport(@PathVariable UUID id) {
        DiscrepancyReportResponse response = validationService.getDiscrepancyReport(id);
        return ResponseEntity.ok(ApiResponse.success("Discrepancy report retrieved successfully", response));
    }

    @GetMapping("/{id}/validation-logs")
    public ResponseEntity<ApiResponse<List<ValidationLogResponse>>> getValidationLogs(@PathVariable UUID id) {
        List<ValidationLogResponse> response = validationService.getValidationLogs(id);
        return ResponseEntity.ok(ApiResponse.success("Validation logs retrieved successfully", response));
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/TransferController.java
git commit -m "feat(controller): add TransferController with all endpoints"
```

---

## Task 17: Integration Tests

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java`

**Step 1: Create integration tests**

```java
package br.com.stockshift.controller;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransferControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BatchRepository batchRepository;

    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private Product testProduct;
    private Batch testBatch;

    @BeforeEach
    void setUp() {
        // Create test data - warehouses, product, batch
        // Implementation depends on your test setup utilities
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = {"ROLE_ADMIN", "TRANSFER_EXECUTE"})
    void shouldCreateTransfer() throws Exception {
        CreateTransferRequest request = CreateTransferRequest.builder()
                .destinationWarehouseId(destinationWarehouse.getId())
                .notes("Test transfer")
                .items(List.of(CreateTransferItemRequest.builder()
                        .sourceBatchId(testBatch.getId())
                        .quantity(new BigDecimal("10"))
                        .build()))
                .build();

        mockMvc.perform(post("/stockshift/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = {"ROLE_ADMIN"})
    void shouldListTransfers() throws Exception {
        mockMvc.perform(get("/stockshift/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = {"ROLE_ADMIN", "TRANSFER_EXECUTE"})
    void shouldExecuteTransfer() throws Exception {
        // Create transfer in DRAFT status first
        // Then execute it

        mockMvc.perform(post("/stockshift/transfers/{id}/execute", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = {"ROLE_ADMIN", "TRANSFER_VALIDATE"})
    void shouldScanBarcode() throws Exception {
        // Create and execute transfer first
        // Then start validation and scan

        ScanBarcodeRequest request = ScanBarcodeRequest.builder()
                .barcode(testProduct.getBarcode())
                .build();

        mockMvc.perform(post("/stockshift/transfers/{id}/scan", transferId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }
}
```

**Step 2: Run tests**

Run: `./gradlew test --tests TransferControllerIntegrationTest`
Expected: Tests should compile (may fail if test setup incomplete)

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java
git commit -m "test: add TransferController integration tests"
```

---

## Task 18: Final Verification

**Step 1: Build the project**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 2: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 3: Start the application**

Run: `./gradlew bootRun`
Expected: Application starts without errors, migrations applied

**Step 4: Final commit with all adjustments**

```bash
git add .
git commit -m "feat: complete transfer system implementation"
```

---

## Summary

| Task | Description | Files |
|------|-------------|-------|
| 1 | Database migrations | 2 migration files |
| 2 | TransferStatus enum | 1 file |
| 3 | Transfer entity | 1 file |
| 4 | TransferItem entity | 1 file |
| 5 | TransferValidationLog entity | 1 file |
| 6 | Update Batch entity | 1 file modified |
| 7 | Update LedgerEntryType | 1 file modified |
| 8 | Repositories | 3 files |
| 9 | Request DTOs | 5 files |
| 10 | Response DTOs | 6 files |
| 11 | TransferMapper | 1 file |
| 12 | TransferStateMachine | 1 file |
| 13 | TransferService (CRUD) | 1 file |
| 14 | TransferService (Execute/Cancel) | 1 file modified |
| 15 | TransferValidationService | 1 file |
| 16 | TransferController | 1 file |
| 17 | Integration tests | 1 file |
| 18 | Final verification | - |

**Total: ~30 new files, ~3 modified files**
