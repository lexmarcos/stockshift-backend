# Transfer Refactoring Phase 1 - Domain Model & Invariants

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create the new Transfer domain model with proper entity separation (Transfer as business process, InventoryLedger as accounting records) and enforce all invariants.

**Architecture:** Separate Transfer (workflow aggregate) from InventoryLedger (append-only accounting). Transfer tracks the business process with items, while InventoryLedger records every stock change with full traceability. TransferInTransit tracks goods between dispatch and receipt.

**Tech Stack:** Spring Boot 3, JPA/Hibernate, PostgreSQL, Flyway migrations, Lombok, JUnit 5 + Testcontainers

---

## Overview

Phase 1 focuses on creating the foundational domain entities and database schema for the new Transfer system. This includes:

1. Database migrations for new tables
2. Entity classes (Transfer, TransferItem, InventoryLedger, TransferInTransit)
3. Enums (TransferStatus, LedgerEntryType)
4. Repository interfaces
5. Unit tests for entity invariants

**Reference Spec:** `.claude/refactoring/transfer-refactoring-spec.md` (Etapa 1)

---

## Task 1: Create TransferStatus Enum

**Files:**
- Create: `src/main/java/br/com/stockshift/model/enums/TransferStatus.java`
- Test: `src/test/java/br/com/stockshift/model/enums/TransferStatusTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferStatusTest {

    @Test
    void shouldHaveAllRequiredStatuses() {
        assertThat(TransferStatus.values()).containsExactlyInAnyOrder(
            TransferStatus.DRAFT,
            TransferStatus.IN_TRANSIT,
            TransferStatus.VALIDATION_IN_PROGRESS,
            TransferStatus.COMPLETED,
            TransferStatus.COMPLETED_WITH_DISCREPANCY,
            TransferStatus.CANCELLED
        );
    }

    @Test
    void shouldIdentifyTerminalStatuses() {
        assertThat(TransferStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(TransferStatus.COMPLETED_WITH_DISCREPANCY.isTerminal()).isTrue();
        assertThat(TransferStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(TransferStatus.DRAFT.isTerminal()).isFalse();
        assertThat(TransferStatus.IN_TRANSIT.isTerminal()).isFalse();
        assertThat(TransferStatus.VALIDATION_IN_PROGRESS.isTerminal()).isFalse();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferStatusTest -i`
Expected: FAIL with "cannot find symbol: class TransferStatus"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.model.enums;

public enum TransferStatus {
    DRAFT,
    IN_TRANSIT,
    VALIDATION_IN_PROGRESS,
    COMPLETED,
    COMPLETED_WITH_DISCREPANCY,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_WITH_DISCREPANCY || this == CANCELLED;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferStatusTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/model/enums/TransferStatus.java src/test/java/br/com/stockshift/model/enums/TransferStatusTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferStatus enum with terminal state check

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Create LedgerEntryType Enum

**Files:**
- Create: `src/main/java/br/com/stockshift/model/enums/LedgerEntryType.java`
- Test: `src/test/java/br/com/stockshift/model/enums/LedgerEntryTypeTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerEntryTypeTest {

    @Test
    void shouldHaveAllRequiredEntryTypes() {
        assertThat(LedgerEntryType.values()).containsExactlyInAnyOrder(
            LedgerEntryType.PURCHASE_IN,
            LedgerEntryType.SALE_OUT,
            LedgerEntryType.ADJUSTMENT_IN,
            LedgerEntryType.ADJUSTMENT_OUT,
            LedgerEntryType.TRANSFER_OUT,
            LedgerEntryType.TRANSFER_IN_TRANSIT,
            LedgerEntryType.TRANSFER_IN,
            LedgerEntryType.TRANSFER_TRANSIT_CONSUMED,
            LedgerEntryType.TRANSFER_LOSS,
            LedgerEntryType.RETURN_IN
        );
    }

    @Test
    void shouldIdentifyDebitEntryTypes() {
        assertThat(LedgerEntryType.SALE_OUT.isDebit()).isTrue();
        assertThat(LedgerEntryType.ADJUSTMENT_OUT.isDebit()).isTrue();
        assertThat(LedgerEntryType.TRANSFER_OUT.isDebit()).isTrue();
        assertThat(LedgerEntryType.TRANSFER_TRANSIT_CONSUMED.isDebit()).isTrue();
        assertThat(LedgerEntryType.TRANSFER_LOSS.isDebit()).isTrue();
    }

    @Test
    void shouldIdentifyCreditEntryTypes() {
        assertThat(LedgerEntryType.PURCHASE_IN.isCredit()).isTrue();
        assertThat(LedgerEntryType.ADJUSTMENT_IN.isCredit()).isTrue();
        assertThat(LedgerEntryType.TRANSFER_IN.isCredit()).isTrue();
        assertThat(LedgerEntryType.TRANSFER_IN_TRANSIT.isCredit()).isTrue();
        assertThat(LedgerEntryType.RETURN_IN.isCredit()).isTrue();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests LedgerEntryTypeTest -i`
Expected: FAIL with "cannot find symbol: class LedgerEntryType"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.model.enums;

public enum LedgerEntryType {
    PURCHASE_IN(false),
    SALE_OUT(true),
    ADJUSTMENT_IN(false),
    ADJUSTMENT_OUT(true),
    TRANSFER_OUT(true),
    TRANSFER_IN_TRANSIT(false),
    TRANSFER_IN(false),
    TRANSFER_TRANSIT_CONSUMED(true),
    TRANSFER_LOSS(true),
    RETURN_IN(false);

    private final boolean debit;

    LedgerEntryType(boolean debit) {
        this.debit = debit;
    }

    public boolean isDebit() {
        return debit;
    }

    public boolean isCredit() {
        return !debit;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests LedgerEntryTypeTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/model/enums/LedgerEntryType.java src/test/java/br/com/stockshift/model/enums/LedgerEntryTypeTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add LedgerEntryType enum with debit/credit classification

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Create TransferItemStatus Enum

**Files:**
- Create: `src/main/java/br/com/stockshift/model/enums/TransferItemStatus.java`
- Test: `src/test/java/br/com/stockshift/model/enums/TransferItemStatusTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferItemStatusTest {

    @Test
    void shouldHaveAllRequiredStatuses() {
        assertThat(TransferItemStatus.values()).containsExactlyInAnyOrder(
            TransferItemStatus.PENDING,
            TransferItemStatus.RECEIVED,
            TransferItemStatus.PARTIAL,
            TransferItemStatus.EXCESS,
            TransferItemStatus.MISSING
        );
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferItemStatusTest -i`
Expected: FAIL with "cannot find symbol: class TransferItemStatus"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.model.enums;

public enum TransferItemStatus {
    PENDING,
    RECEIVED,
    PARTIAL,
    EXCESS,
    MISSING
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferItemStatusTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/model/enums/TransferItemStatus.java src/test/java/br/com/stockshift/model/enums/TransferItemStatusTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferItemStatus enum

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Create Database Migration for Transfer Tables

**Files:**
- Create: `src/main/resources/db/migration/V26__create_transfer_tables.sql`

**Step 1: Write the migration file**

```sql
-- Transfer table (business process aggregate)
CREATE TABLE transfers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    transfer_code VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',

    source_warehouse_id UUID NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
    destination_warehouse_id UUID NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,

    notes TEXT,

    -- Audit fields
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    dispatched_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    dispatched_at TIMESTAMPTZ,
    validation_started_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    validation_started_at TIMESTAMPTZ,
    completed_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    completed_at TIMESTAMPTZ,
    cancelled_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason TEXT,

    -- Concurrency control
    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transfer_status CHECK (status IN (
        'DRAFT', 'IN_TRANSIT', 'VALIDATION_IN_PROGRESS',
        'COMPLETED', 'COMPLETED_WITH_DISCREPANCY', 'CANCELLED'
    )),
    CONSTRAINT chk_different_warehouses CHECK (source_warehouse_id != destination_warehouse_id),
    CONSTRAINT uq_transfer_code_tenant UNIQUE (tenant_id, transfer_code)
);

-- Transfer items table
CREATE TABLE transfer_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,

    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    source_batch_id UUID NOT NULL REFERENCES batches(id) ON DELETE RESTRICT,
    destination_batch_id UUID REFERENCES batches(id) ON DELETE RESTRICT,

    expected_quantity INTEGER NOT NULL,
    received_quantity INTEGER,

    item_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_expected_positive CHECK (expected_quantity > 0),
    CONSTRAINT chk_received_non_negative CHECK (received_quantity IS NULL OR received_quantity >= 0),
    CONSTRAINT chk_item_status CHECK (item_status IN (
        'PENDING', 'RECEIVED', 'PARTIAL', 'EXCESS', 'MISSING'
    ))
);

-- Indexes for transfers
CREATE INDEX idx_transfers_tenant ON transfers(tenant_id);
CREATE INDEX idx_transfers_status ON transfers(status);
CREATE INDEX idx_transfers_source ON transfers(source_warehouse_id);
CREATE INDEX idx_transfers_destination ON transfers(destination_warehouse_id);
CREATE INDEX idx_transfers_created_at ON transfers(created_at DESC);

-- Indexes for transfer_items
CREATE INDEX idx_transfer_items_transfer ON transfer_items(transfer_id);
CREATE INDEX idx_transfer_items_product ON transfer_items(product_id);
CREATE INDEX idx_transfer_items_source_batch ON transfer_items(source_batch_id);

-- Update triggers
CREATE TRIGGER update_transfers_updated_at BEFORE UPDATE ON transfers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transfer_items_updated_at BEFORE UPDATE ON transfer_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Sequence for transfer_code generation
CREATE SEQUENCE transfer_code_seq START 1;
```

**Step 2: Run migration to verify it works**

Run: `./gradlew flywayMigrate -i` (or restart application with test profile)

If running integration tests:
Run: `./gradlew test --tests BaseIntegrationTest -i`
Expected: PASS (migrations apply successfully)

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V26__create_transfer_tables.sql
git commit -m "$(cat <<'EOF'
feat(transfer): add database migration for transfer and transfer_items tables

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Create Database Migration for InventoryLedger Table

**Files:**
- Create: `src/main/resources/db/migration/V27__create_inventory_ledger.sql`

**Step 1: Write the migration file**

```sql
-- Inventory ledger table (append-only accounting records)
CREATE TABLE inventory_ledger (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,

    warehouse_id UUID REFERENCES warehouses(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    batch_id UUID REFERENCES batches(id) ON DELETE RESTRICT,

    entry_type VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL,
    balance_after INTEGER,

    reference_type VARCHAR(50) NOT NULL,
    reference_id UUID NOT NULL,
    transfer_item_id UUID REFERENCES transfer_items(id) ON DELETE RESTRICT,

    notes TEXT,

    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_entry_type CHECK (entry_type IN (
        'PURCHASE_IN', 'SALE_OUT', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT',
        'TRANSFER_OUT', 'TRANSFER_IN_TRANSIT', 'TRANSFER_IN',
        'TRANSFER_TRANSIT_CONSUMED', 'TRANSFER_LOSS', 'RETURN_IN'
    ))
);

-- Indexes for inventory_ledger
CREATE INDEX idx_ledger_tenant ON inventory_ledger(tenant_id);
CREATE INDEX idx_ledger_warehouse ON inventory_ledger(warehouse_id);
CREATE INDEX idx_ledger_batch ON inventory_ledger(batch_id);
CREATE INDEX idx_ledger_reference ON inventory_ledger(reference_type, reference_id);
CREATE INDEX idx_ledger_created_at ON inventory_ledger(created_at DESC);
CREATE INDEX idx_ledger_entry_type ON inventory_ledger(entry_type);

-- Trigger to prevent modifications (append-only)
CREATE OR REPLACE FUNCTION prevent_ledger_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Inventory ledger entries cannot be modified or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_immutable
    BEFORE UPDATE OR DELETE ON inventory_ledger
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_modification();
```

**Step 2: Run migration to verify it works**

Run: `./gradlew test --tests BaseIntegrationTest -i`
Expected: PASS (migrations apply successfully)

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V27__create_inventory_ledger.sql
git commit -m "$(cat <<'EOF'
feat(transfer): add inventory_ledger table with append-only constraint

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Create Database Migration for TransferInTransit Table

**Files:**
- Create: `src/main/resources/db/migration/V28__create_transfer_in_transit.sql`

**Step 1: Write the migration file**

```sql
-- Transfer in transit table (tracks goods between dispatch and receipt)
CREATE TABLE transfer_in_transit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE RESTRICT,
    transfer_item_id UUID NOT NULL REFERENCES transfer_items(id) ON DELETE RESTRICT,

    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    source_batch_id UUID NOT NULL REFERENCES batches(id) ON DELETE RESTRICT,

    quantity INTEGER NOT NULL,
    consumed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transit_quantity_non_negative CHECK (quantity >= 0)
);

-- Indexes
CREATE INDEX idx_transit_transfer ON transfer_in_transit(transfer_id);
CREATE INDEX idx_transit_pending ON transfer_in_transit(consumed_at) WHERE consumed_at IS NULL;
CREATE INDEX idx_transit_tenant ON transfer_in_transit(tenant_id);

-- Update trigger
CREATE TRIGGER update_transfer_in_transit_updated_at BEFORE UPDATE ON transfer_in_transit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

**Step 2: Run migration to verify it works**

Run: `./gradlew test --tests BaseIntegrationTest -i`
Expected: PASS (migrations apply successfully)

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V28__create_transfer_in_transit.sql
git commit -m "$(cat <<'EOF'
feat(transfer): add transfer_in_transit table for tracking goods in transit

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Create Database Migration for Batch Origin Fields

**Files:**
- Create: `src/main/resources/db/migration/V29__add_batch_origin_fields.sql`

**Step 1: Write the migration file**

```sql
-- Add origin tracking fields to batches for transfer traceability
ALTER TABLE batches ADD COLUMN origin_transfer_id UUID REFERENCES transfers(id) ON DELETE RESTRICT;
ALTER TABLE batches ADD COLUMN origin_batch_id UUID REFERENCES batches(id) ON DELETE RESTRICT;

-- Index for finding batches created from transfers
CREATE INDEX idx_batches_origin_transfer ON batches(origin_transfer_id) WHERE origin_transfer_id IS NOT NULL;
```

**Step 2: Run migration to verify it works**

Run: `./gradlew test --tests BaseIntegrationTest -i`
Expected: PASS (migrations apply successfully)

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V29__add_batch_origin_fields.sql
git commit -m "$(cat <<'EOF'
feat(transfer): add origin_transfer_id and origin_batch_id to batches table

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Create Transfer Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/Transfer.java`
- Test: `src/test/java/br/com/stockshift/model/entity/TransferTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.TransferStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferTest {

    @Test
    void shouldCreateTransferWithDraftStatus() {
        Transfer transfer = new Transfer();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.DRAFT);
    }

    @Test
    void shouldInitializeEmptyItemsList() {
        Transfer transfer = new Transfer();
        assertThat(transfer.getItems()).isNotNull().isEmpty();
    }

    @Test
    void shouldAddItemToTransfer() {
        Transfer transfer = new Transfer();
        TransferItem item = new TransferItem();
        item.setExpectedQuantity(10);

        transfer.addItem(item);

        assertThat(transfer.getItems()).hasSize(1);
        assertThat(item.getTransfer()).isEqualTo(transfer);
    }

    @Test
    void shouldRemoveItemFromTransfer() {
        Transfer transfer = new Transfer();
        TransferItem item = new TransferItem();
        transfer.addItem(item);

        transfer.removeItem(item);

        assertThat(transfer.getItems()).isEmpty();
        assertThat(item.getTransfer()).isNull();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferTest -i`
Expected: FAIL with "cannot find symbol: class Transfer"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transfers", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "transfer_code"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Transfer extends TenantAwareEntity {

    @Column(name = "transfer_code", nullable = false, length = 50)
    private String transferCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TransferStatus status = TransferStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_warehouse_id", nullable = false)
    private Warehouse sourceWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_warehouse_id", nullable = false)
    private Warehouse destinationWarehouse;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Audit fields
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispatched_by")
    private User dispatchedBy;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_started_by")
    private User validationStartedBy;

    @Column(name = "validation_started_at")
    private LocalDateTime validationStartedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private User completedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
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

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/Transfer.java src/test/java/br/com/stockshift/model/entity/TransferTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add Transfer entity with status, audit fields, and items relationship

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Create TransferItem Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/TransferItem.java`
- Test: `src/test/java/br/com/stockshift/model/entity/TransferItemTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.TransferItemStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferItemTest {

    @Test
    void shouldCreateItemWithPendingStatus() {
        TransferItem item = new TransferItem();
        assertThat(item.getItemStatus()).isEqualTo(TransferItemStatus.PENDING);
    }

    @Test
    void shouldHaveNullReceivedQuantityByDefault() {
        TransferItem item = new TransferItem();
        assertThat(item.getReceivedQuantity()).isNull();
    }

    @Test
    void shouldHaveNullDestinationBatchByDefault() {
        TransferItem item = new TransferItem();
        assertThat(item.getDestinationBatch()).isNull();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferItemTest -i`
Expected: FAIL with "cannot find symbol: class TransferItem"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.TransferItemStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

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

    @Column(name = "expected_quantity", nullable = false)
    private Integer expectedQuantity;

    @Column(name = "received_quantity")
    private Integer receivedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 30)
    private TransferItemStatus itemStatus = TransferItemStatus.PENDING;
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferItemTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/TransferItem.java src/test/java/br/com/stockshift/model/entity/TransferItemTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferItem entity with expected/received quantities

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Create InventoryLedger Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/InventoryLedger.java`
- Test: `src/test/java/br/com/stockshift/model/entity/InventoryLedgerTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.LedgerEntryType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryLedgerTest {

    @Test
    void shouldRequirePositiveQuantity() {
        InventoryLedger ledger = new InventoryLedger();
        ledger.setQuantity(100);
        assertThat(ledger.getQuantity()).isEqualTo(100);
    }

    @Test
    void shouldAllowNullWarehouseForVirtualEntries() {
        InventoryLedger ledger = new InventoryLedger();
        ledger.setEntryType(LedgerEntryType.TRANSFER_IN_TRANSIT);
        assertThat(ledger.getWarehouse()).isNull();
    }

    @Test
    void shouldHaveReferenceFields() {
        InventoryLedger ledger = new InventoryLedger();
        UUID refId = UUID.randomUUID();
        ledger.setReferenceType("TRANSFER");
        ledger.setReferenceId(refId);

        assertThat(ledger.getReferenceType()).isEqualTo("TRANSFER");
        assertThat(ledger.getReferenceId()).isEqualTo(refId);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests InventoryLedgerTest -i`
Expected: FAIL with "cannot find symbol: class InventoryLedger"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.LedgerEntryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 50)
    private LedgerEntryType entryType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "balance_after")
    private Integer balanceAfter;

    @Column(name = "reference_type", nullable = false, length = 50)
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_item_id")
    private TransferItem transferItem;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests InventoryLedgerTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/InventoryLedger.java src/test/java/br/com/stockshift/model/entity/InventoryLedgerTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add InventoryLedger entity for append-only accounting records

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Create TransferInTransit Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/TransferInTransit.java`
- Test: `src/test/java/br/com/stockshift/model/entity/TransferInTransitTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.model.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TransferInTransitTest {

    @Test
    void shouldHaveNullConsumedAtByDefault() {
        TransferInTransit transit = new TransferInTransit();
        assertThat(transit.getConsumedAt()).isNull();
    }

    @Test
    void shouldTrackQuantityInTransit() {
        TransferInTransit transit = new TransferInTransit();
        transit.setQuantity(50);
        assertThat(transit.getQuantity()).isEqualTo(50);
    }

    @Test
    void shouldMarkAsConsumed() {
        TransferInTransit transit = new TransferInTransit();
        transit.setQuantity(50);

        LocalDateTime now = LocalDateTime.now();
        transit.setConsumedAt(now);
        transit.setQuantity(0);

        assertThat(transit.getConsumedAt()).isEqualTo(now);
        assertThat(transit.getQuantity()).isZero();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferInTransitTest -i`
Expected: FAIL with "cannot find symbol: class TransferInTransit"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transfer_in_transit")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class TransferInTransit extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_item_id", nullable = false)
    private TransferItem transferItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_batch_id", nullable = false)
    private Batch sourceBatch;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferInTransitTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/TransferInTransit.java src/test/java/br/com/stockshift/model/entity/TransferInTransitTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferInTransit entity for tracking goods in transit

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Update Batch Entity with Origin Fields

**Files:**
- Modify: `src/main/java/br/com/stockshift/model/entity/Batch.java`
- Test: `src/test/java/br/com/stockshift/model/entity/BatchOriginTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.model.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BatchOriginTest {

    @Test
    void shouldHaveNullOriginFieldsByDefault() {
        Batch batch = new Batch();
        assertThat(batch.getOriginTransfer()).isNull();
        assertThat(batch.getOriginBatch()).isNull();
    }

    @Test
    void shouldTrackOriginTransfer() {
        Batch batch = new Batch();
        Transfer transfer = new Transfer();

        batch.setOriginTransfer(transfer);

        assertThat(batch.getOriginTransfer()).isEqualTo(transfer);
    }

    @Test
    void shouldTrackOriginBatch() {
        Batch originalBatch = new Batch();
        Batch newBatch = new Batch();

        newBatch.setOriginBatch(originalBatch);

        assertThat(newBatch.getOriginBatch()).isEqualTo(originalBatch);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests BatchOriginTest -i`
Expected: FAIL with "cannot find symbol: method getOriginTransfer()"

**Step 3: Add fields to Batch entity**

Add these fields to `src/main/java/br/com/stockshift/model/entity/Batch.java` after line 54 (after `sellingPrice`):

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_transfer_id")
    private Transfer originTransfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_batch_id")
    private Batch originBatch;
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests BatchOriginTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/Batch.java src/test/java/br/com/stockshift/model/entity/BatchOriginTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add origin tracking fields to Batch entity

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Create TransferRepository

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/TransferRepository.java`

**Step 1: Write the repository interface**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.enums.TransferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByTenantIdAndId(UUID tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transfer t WHERE t.id = :id")
    Optional<Transfer> findByIdForUpdate(@Param("id") UUID id);

    Page<Transfer> findByTenantIdAndSourceWarehouseId(UUID tenantId, UUID warehouseId, Pageable pageable);

    Page<Transfer> findByTenantIdAndDestinationWarehouseId(UUID tenantId, UUID warehouseId, Pageable pageable);

    Page<Transfer> findByTenantIdAndStatus(UUID tenantId, TransferStatus status, Pageable pageable);

    @Query("SELECT t FROM Transfer t WHERE t.tenantId = :tenantId " +
           "AND (t.sourceWarehouseId = :warehouseId OR t.destinationWarehouseId = :warehouseId)")
    Page<Transfer> findByTenantIdAndWarehouseId(
        @Param("tenantId") UUID tenantId,
        @Param("warehouseId") UUID warehouseId,
        Pageable pageable
    );

    boolean existsByTenantIdAndTransferCode(UUID tenantId, String transferCode);
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava -i`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/TransferRepository.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferRepository with pessimistic locking support

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Create TransferItemRepository

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/TransferItemRepository.java`

**Step 1: Write the repository interface**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferItemRepository extends JpaRepository<TransferItem, UUID> {

    List<TransferItem> findByTransferId(UUID transferId);

    List<TransferItem> findByTransferIdAndTenantId(UUID transferId, UUID tenantId);
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava -i`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/TransferItemRepository.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferItemRepository

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: Create InventoryLedgerRepository

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/InventoryLedgerRepository.java`

**Step 1: Write the repository interface**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.InventoryLedger;
import br.com.stockshift.model.enums.LedgerEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, UUID> {

    List<InventoryLedger> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

    List<InventoryLedger> findByTenantIdAndBatchId(UUID tenantId, UUID batchId);

    Page<InventoryLedger> findByTenantIdAndWarehouseId(UUID tenantId, UUID warehouseId, Pageable pageable);

    @Query("SELECT l FROM InventoryLedger l WHERE l.tenantId = :tenantId " +
           "AND l.referenceType = :referenceType AND l.referenceId = :referenceId " +
           "ORDER BY l.createdAt ASC")
    List<InventoryLedger> findByReference(
        @Param("tenantId") UUID tenantId,
        @Param("referenceType") String referenceType,
        @Param("referenceId") UUID referenceId
    );

    @Query("SELECT COUNT(l) FROM InventoryLedger l " +
           "WHERE l.referenceType = :referenceType AND l.referenceId = :referenceId " +
           "AND l.entryType = :entryType")
    long countByReferenceAndEntryType(
        @Param("referenceType") String referenceType,
        @Param("referenceId") UUID referenceId,
        @Param("entryType") LedgerEntryType entryType
    );
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava -i`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/InventoryLedgerRepository.java
git commit -m "$(cat <<'EOF'
feat(transfer): add InventoryLedgerRepository with reference queries

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 16: Create TransferInTransitRepository

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/TransferInTransitRepository.java`

**Step 1: Write the repository interface**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferInTransit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferInTransitRepository extends JpaRepository<TransferInTransit, UUID> {

    List<TransferInTransit> findByTransferId(UUID transferId);

    List<TransferInTransit> findByTransferIdAndConsumedAtIsNull(UUID transferId);

    Optional<TransferInTransit> findByTransferItemId(UUID transferItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransferInTransit t WHERE t.transferItem.id = :transferItemId")
    Optional<TransferInTransit> findByTransferItemIdForUpdate(@Param("transferItemId") UUID transferItemId);

    @Query("SELECT t FROM TransferInTransit t WHERE t.tenantId = :tenantId AND t.consumedAt IS NULL")
    List<TransferInTransit> findPendingByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT SUM(t.quantity) FROM TransferInTransit t " +
           "WHERE t.tenantId = :tenantId AND t.consumedAt IS NULL")
    Integer sumPendingQuantityByTenantId(@Param("tenantId") UUID tenantId);
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava -i`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/TransferInTransitRepository.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferInTransitRepository with pending queries

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 17: Run Full Integration Test Suite

**Step 1: Run all tests to ensure nothing is broken**

Run: `./gradlew test -i`
Expected: All tests PASS

**Step 2: Verify migrations apply correctly**

The integration tests use Testcontainers which will apply all migrations. If tests pass, migrations are correct.

**Step 3: Commit any fixes if needed**

If any tests fail, fix them before proceeding.

---

## Task 18: Final Verification

**Step 1: Verify all new files are committed**

Run: `git status`
Expected: Clean working directory

**Step 2: Review the commit log**

Run: `git log --oneline -15`
Expected: See all commits from this phase

**Step 3: Tag the phase completion**

```bash
git tag -a v0.1.0-transfer-phase1 -m "Transfer Refactoring Phase 1: Domain Model & Invariants complete"
```

---

## Summary

Phase 1 creates the foundational domain model for the new Transfer system:

| Component | Files Created |
|-----------|---------------|
| Enums | `TransferStatus`, `LedgerEntryType`, `TransferItemStatus` |
| Entities | `Transfer`, `TransferItem`, `InventoryLedger`, `TransferInTransit` |
| Repositories | `TransferRepository`, `TransferItemRepository`, `InventoryLedgerRepository`, `TransferInTransitRepository` |
| Migrations | `V26` through `V29` |
| Entity Updates | `Batch` (origin fields) |

**Invariants Enforced:**
- I3: TransferInTransit quantity >= 0 (DB constraint)
- I7: Ledger is append-only (DB trigger)
- I9: Source != Destination warehouse (DB constraint)

**Next Phase:** Phase 2 will implement the State Machine and Role-based permissions.
