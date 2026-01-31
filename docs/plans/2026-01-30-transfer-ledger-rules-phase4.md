# Transfer Ledger Rules (Phase 4) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement complete ledger-based stock tracking for transfers with discrepancy management, divergence policies, and reconciliation capabilities.

**Architecture:** Refactor TransferDiscrepancy to link to TransferItem instead of StockMovementItem. Add enums for discrepancy types/status/resolution. Implement discrepancy creation during validation completion, resolution endpoints with TRANSFER_LOSS ledger entries, and a reconciliation job for batch quantity verification.

**Tech Stack:** Java 21, Spring Boot 3, JPA/Hibernate, PostgreSQL, Flyway migrations

---

## Task 1: Create Discrepancy Enums

**Files:**
- Create: `src/main/java/br/com/stockshift/model/enums/DiscrepancyType.java`
- Create: `src/main/java/br/com/stockshift/model/enums/DiscrepancyStatus.java`
- Create: `src/main/java/br/com/stockshift/model/enums/DiscrepancyResolution.java`
- Test: `src/test/java/br/com/stockshift/model/enums/DiscrepancyEnumsTest.java`

**Step 1: Write the failing test for DiscrepancyType**

```java
package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DiscrepancyEnumsTest {

    @Test
    void discrepancyTypeShouldHaveShortageAndExcess() {
        assertThat(DiscrepancyType.values()).containsExactlyInAnyOrder(
            DiscrepancyType.SHORTAGE,
            DiscrepancyType.EXCESS
        );
    }

    @Test
    void discrepancyStatusShouldHaveAllStatuses() {
        assertThat(DiscrepancyStatus.values()).containsExactlyInAnyOrder(
            DiscrepancyStatus.PENDING_RESOLUTION,
            DiscrepancyStatus.RESOLVED,
            DiscrepancyStatus.WRITTEN_OFF
        );
    }

    @Test
    void discrepancyResolutionShouldHaveAllResolutions() {
        assertThat(DiscrepancyResolution.values()).containsExactlyInAnyOrder(
            DiscrepancyResolution.WRITE_OFF,
            DiscrepancyResolution.FOUND,
            DiscrepancyResolution.RETURN_TRANSIT,
            DiscrepancyResolution.ACCEPTED
        );
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.model.enums.DiscrepancyEnumsTest" --info`
Expected: FAIL with "cannot find symbol: class DiscrepancyType"

**Step 3: Create DiscrepancyType enum**

```java
package br.com.stockshift.model.enums;

public enum DiscrepancyType {
    SHORTAGE,  // Received less than expected
    EXCESS     // Received more than expected
}
```

**Step 4: Create DiscrepancyStatus enum**

```java
package br.com.stockshift.model.enums;

public enum DiscrepancyStatus {
    PENDING_RESOLUTION,  // Awaiting resolution
    RESOLVED,            // Resolved with action taken
    WRITTEN_OFF          // Written off as loss
}
```

**Step 5: Create DiscrepancyResolution enum**

```java
package br.com.stockshift.model.enums;

public enum DiscrepancyResolution {
    WRITE_OFF,       // Write off as loss
    FOUND,           // Item was found, create manual entry
    RETURN_TRANSIT,  // Return to origin (creates reverse transfer)
    ACCEPTED         // Accept excess with audit flag
}
```

**Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.stockshift.model.enums.DiscrepancyEnumsTest" --info`
Expected: PASS

**Step 7: Commit**

```bash
git add src/main/java/br/com/stockshift/model/enums/DiscrepancyType.java \
        src/main/java/br/com/stockshift/model/enums/DiscrepancyStatus.java \
        src/main/java/br/com/stockshift/model/enums/DiscrepancyResolution.java \
        src/test/java/br/com/stockshift/model/enums/DiscrepancyEnumsTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add discrepancy enums for Phase 4 ledger rules

Add DiscrepancyType (SHORTAGE/EXCESS), DiscrepancyStatus
(PENDING_RESOLUTION/RESOLVED/WRITTEN_OFF), and DiscrepancyResolution
(WRITE_OFF/FOUND/RETURN_TRANSIT/ACCEPTED) enums.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Create New TransferDiscrepancy Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/NewTransferDiscrepancy.java`
- Modify: `src/main/java/br/com/stockshift/repository/TransferDiscrepancyRepository.java`
- Create: `src/main/resources/db/migration/V31__create_new_transfer_discrepancy.sql`
- Test: `src/test/java/br/com/stockshift/model/entity/NewTransferDiscrepancyTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.DiscrepancyResolution;
import br.com.stockshift.model.enums.DiscrepancyStatus;
import br.com.stockshift.model.enums.DiscrepancyType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NewTransferDiscrepancyTest {

    @Test
    void shouldCreateDiscrepancyWithAllFields() {
        NewTransferDiscrepancy discrepancy = new NewTransferDiscrepancy();
        discrepancy.setTenantId(UUID.randomUUID());
        discrepancy.setDiscrepancyType(DiscrepancyType.SHORTAGE);
        discrepancy.setExpectedQuantity(new BigDecimal("50"));
        discrepancy.setReceivedQuantity(new BigDecimal("40"));
        discrepancy.setDifference(new BigDecimal("10"));
        discrepancy.setStatus(DiscrepancyStatus.PENDING_RESOLUTION);

        assertThat(discrepancy.getDiscrepancyType()).isEqualTo(DiscrepancyType.SHORTAGE);
        assertThat(discrepancy.getStatus()).isEqualTo(DiscrepancyStatus.PENDING_RESOLUTION);
        assertThat(discrepancy.getDifference()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void shouldAllowResolution() {
        NewTransferDiscrepancy discrepancy = new NewTransferDiscrepancy();
        discrepancy.setStatus(DiscrepancyStatus.RESOLVED);
        discrepancy.setResolution(DiscrepancyResolution.WRITE_OFF);
        discrepancy.setResolutionNotes("Damage during transport");

        assertThat(discrepancy.getStatus()).isEqualTo(DiscrepancyStatus.RESOLVED);
        assertThat(discrepancy.getResolution()).isEqualTo(DiscrepancyResolution.WRITE_OFF);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.model.entity.NewTransferDiscrepancyTest" --info`
Expected: FAIL with "cannot find symbol: class NewTransferDiscrepancy"

**Step 3: Create the NewTransferDiscrepancy entity**

```java
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
import java.util.UUID;

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
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.stockshift.model.entity.NewTransferDiscrepancyTest" --info`
Expected: PASS

**Step 5: Create the database migration**

```sql
-- V31__create_new_transfer_discrepancy.sql
CREATE TABLE transfer_discrepancy (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE RESTRICT,
    transfer_item_id UUID NOT NULL REFERENCES transfer_items(id) ON DELETE RESTRICT,

    discrepancy_type VARCHAR(20) NOT NULL,
    expected_quantity DECIMAL(15,3) NOT NULL,
    received_quantity DECIMAL(15,3) NOT NULL,
    difference DECIMAL(15,3) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_RESOLUTION',
    resolution VARCHAR(30),
    resolution_notes TEXT,
    resolved_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    resolved_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_discrepancy_type CHECK (discrepancy_type IN ('SHORTAGE', 'EXCESS')),
    CONSTRAINT chk_discrepancy_status CHECK (status IN (
        'PENDING_RESOLUTION', 'RESOLVED', 'WRITTEN_OFF'
    )),
    CONSTRAINT chk_resolution CHECK (resolution IS NULL OR resolution IN (
        'WRITE_OFF', 'FOUND', 'RETURN_TRANSIT', 'ACCEPTED'
    ))
);

CREATE INDEX idx_discrepancy_tenant ON transfer_discrepancy(tenant_id);
CREATE INDEX idx_discrepancy_transfer ON transfer_discrepancy(transfer_id);
CREATE INDEX idx_discrepancy_item ON transfer_discrepancy(transfer_item_id);
CREATE INDEX idx_discrepancy_pending ON transfer_discrepancy(status) WHERE status = 'PENDING_RESOLUTION';
```

**Step 6: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/NewTransferDiscrepancy.java \
        src/main/resources/db/migration/V31__create_new_transfer_discrepancy.sql \
        src/test/java/br/com/stockshift/model/entity/NewTransferDiscrepancyTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add NewTransferDiscrepancy entity for Phase 4

Create entity linking to Transfer and TransferItem with proper
discrepancy tracking including type, status, resolution fields.
Add migration V31 for transfer_discrepancy table.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Create NewTransferDiscrepancyRepository

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/NewTransferDiscrepancyRepository.java`
- Test: `src/test/java/br/com/stockshift/repository/NewTransferDiscrepancyRepositoryTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.NewTransferDiscrepancy;
import br.com.stockshift.model.enums.DiscrepancyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class NewTransferDiscrepancyRepositoryTest {

    @Autowired
    private NewTransferDiscrepancyRepository repository;

    @Test
    void shouldFindByTransferId() {
        UUID transferId = UUID.randomUUID();
        List<NewTransferDiscrepancy> result = repository.findByTransferId(transferId);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldFindByTransferIdAndStatus() {
        UUID transferId = UUID.randomUUID();
        List<NewTransferDiscrepancy> result = repository.findByTransferIdAndStatus(
            transferId, DiscrepancyStatus.PENDING_RESOLUTION
        );
        assertThat(result).isNotNull();
    }

    @Test
    void shouldFindPendingByTenantId() {
        UUID tenantId = UUID.randomUUID();
        List<NewTransferDiscrepancy> result = repository.findPendingByTenantId(tenantId);
        assertThat(result).isNotNull();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.repository.NewTransferDiscrepancyRepositoryTest" --info`
Expected: FAIL with "cannot find symbol: class NewTransferDiscrepancyRepository"

**Step 3: Create the repository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.NewTransferDiscrepancy;
import br.com.stockshift.model.enums.DiscrepancyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NewTransferDiscrepancyRepository extends JpaRepository<NewTransferDiscrepancy, UUID> {

    List<NewTransferDiscrepancy> findByTransferId(UUID transferId);

    List<NewTransferDiscrepancy> findByTransferIdAndStatus(UUID transferId, DiscrepancyStatus status);

    @Query("SELECT d FROM NewTransferDiscrepancy d WHERE d.tenantId = :tenantId AND d.status = 'PENDING_RESOLUTION'")
    List<NewTransferDiscrepancy> findPendingByTenantId(@Param("tenantId") UUID tenantId);

    List<NewTransferDiscrepancy> findByTransferItemId(UUID transferItemId);
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.stockshift.repository.NewTransferDiscrepancyRepositoryTest" --info`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/NewTransferDiscrepancyRepository.java \
        src/test/java/br/com/stockshift/repository/NewTransferDiscrepancyRepositoryTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add NewTransferDiscrepancyRepository

Add repository with queries for finding discrepancies by transfer,
status, and tenant. Supports pending discrepancy tracking.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Create DiscrepancyService

**Files:**
- Create: `src/main/java/br/com/stockshift/service/transfer/DiscrepancyService.java`
- Test: `src/test/java/br/com/stockshift/service/transfer/DiscrepancyServiceTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.*;
import br.com.stockshift.repository.NewTransferDiscrepancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscrepancyServiceTest {

    @Mock
    private NewTransferDiscrepancyRepository discrepancyRepository;

    @Captor
    private ArgumentCaptor<NewTransferDiscrepancy> discrepancyCaptor;

    private DiscrepancyService discrepancyService;

    private Transfer transfer;
    private TransferItem item;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        discrepancyService = new DiscrepancyService(discrepancyRepository);

        tenantId = UUID.randomUUID();

        transfer = new Transfer();
        transfer.setId(UUID.randomUUID());
        transfer.setTenantId(tenantId);

        item = new TransferItem();
        item.setId(UUID.randomUUID());
        item.setTenantId(tenantId);
        item.setExpectedQuantity(new BigDecimal("50"));
    }

    @Nested
    class CreateDiscrepancy {

        @Test
        void shouldCreateShortageDiscrepancy() {
            item.setReceivedQuantity(new BigDecimal("40"));

            when(discrepancyRepository.save(any())).thenAnswer(inv -> {
                NewTransferDiscrepancy d = inv.getArgument(0);
                d.setId(UUID.randomUUID());
                return d;
            });

            NewTransferDiscrepancy result = discrepancyService.createDiscrepancy(
                transfer, item, DiscrepancyType.SHORTAGE
            );

            assertThat(result.getDiscrepancyType()).isEqualTo(DiscrepancyType.SHORTAGE);
            assertThat(result.getExpectedQuantity()).isEqualByComparingTo(new BigDecimal("50"));
            assertThat(result.getReceivedQuantity()).isEqualByComparingTo(new BigDecimal("40"));
            assertThat(result.getDifference()).isEqualByComparingTo(new BigDecimal("10"));
            assertThat(result.getStatus()).isEqualTo(DiscrepancyStatus.PENDING_RESOLUTION);
        }

        @Test
        void shouldCreateExcessDiscrepancy() {
            item.setReceivedQuantity(new BigDecimal("60"));

            when(discrepancyRepository.save(any())).thenAnswer(inv -> {
                NewTransferDiscrepancy d = inv.getArgument(0);
                d.setId(UUID.randomUUID());
                return d;
            });

            NewTransferDiscrepancy result = discrepancyService.createDiscrepancy(
                transfer, item, DiscrepancyType.EXCESS
            );

            assertThat(result.getDiscrepancyType()).isEqualTo(DiscrepancyType.EXCESS);
            assertThat(result.getDifference()).isEqualByComparingTo(new BigDecimal("10"));
        }
    }

    @Nested
    class EvaluateValidation {

        @Test
        void shouldDetectNoDiscrepancy() {
            item.setReceivedQuantity(new BigDecimal("50"));

            DiscrepancyService.ValidationResult result = discrepancyService.evaluateItem(item);

            assertThat(result.hasDiscrepancy()).isFalse();
            assertThat(result.discrepancyType()).isNull();
        }

        @Test
        void shouldDetectShortage() {
            item.setReceivedQuantity(new BigDecimal("40"));

            DiscrepancyService.ValidationResult result = discrepancyService.evaluateItem(item);

            assertThat(result.hasDiscrepancy()).isTrue();
            assertThat(result.discrepancyType()).isEqualTo(DiscrepancyType.SHORTAGE);
            assertThat(result.difference()).isEqualByComparingTo(new BigDecimal("10"));
        }

        @Test
        void shouldDetectExcess() {
            item.setReceivedQuantity(new BigDecimal("60"));

            DiscrepancyService.ValidationResult result = discrepancyService.evaluateItem(item);

            assertThat(result.hasDiscrepancy()).isTrue();
            assertThat(result.discrepancyType()).isEqualTo(DiscrepancyType.EXCESS);
            assertThat(result.difference()).isEqualByComparingTo(new BigDecimal("10"));
        }

        @Test
        void shouldTreatNullReceivedAsZero() {
            item.setReceivedQuantity(null);

            DiscrepancyService.ValidationResult result = discrepancyService.evaluateItem(item);

            assertThat(result.hasDiscrepancy()).isTrue();
            assertThat(result.discrepancyType()).isEqualTo(DiscrepancyType.SHORTAGE);
            assertThat(result.difference()).isEqualByComparingTo(new BigDecimal("50"));
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.service.transfer.DiscrepancyServiceTest" --info`
Expected: FAIL with "cannot find symbol: class DiscrepancyService"

**Step 3: Create the DiscrepancyService**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.model.entity.NewTransferDiscrepancy;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferItem;
import br.com.stockshift.model.enums.DiscrepancyStatus;
import br.com.stockshift.model.enums.DiscrepancyType;
import br.com.stockshift.repository.NewTransferDiscrepancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscrepancyService {

    private final NewTransferDiscrepancyRepository discrepancyRepository;

    public record ValidationResult(
        boolean hasDiscrepancy,
        DiscrepancyType discrepancyType,
        BigDecimal difference
    ) {}

    public ValidationResult evaluateItem(TransferItem item) {
        BigDecimal expected = item.getExpectedQuantity();
        BigDecimal received = item.getReceivedQuantity() != null
            ? item.getReceivedQuantity()
            : BigDecimal.ZERO;

        int comparison = received.compareTo(expected);

        if (comparison == 0) {
            return new ValidationResult(false, null, BigDecimal.ZERO);
        } else if (comparison < 0) {
            BigDecimal difference = expected.subtract(received);
            return new ValidationResult(true, DiscrepancyType.SHORTAGE, difference);
        } else {
            BigDecimal difference = received.subtract(expected);
            return new ValidationResult(true, DiscrepancyType.EXCESS, difference);
        }
    }

    public NewTransferDiscrepancy createDiscrepancy(
        Transfer transfer,
        TransferItem item,
        DiscrepancyType type
    ) {
        BigDecimal expected = item.getExpectedQuantity();
        BigDecimal received = item.getReceivedQuantity() != null
            ? item.getReceivedQuantity()
            : BigDecimal.ZERO;
        BigDecimal difference = type == DiscrepancyType.SHORTAGE
            ? expected.subtract(received)
            : received.subtract(expected);

        NewTransferDiscrepancy discrepancy = new NewTransferDiscrepancy();
        discrepancy.setTenantId(transfer.getTenantId());
        discrepancy.setTransfer(transfer);
        discrepancy.setTransferItem(item);
        discrepancy.setDiscrepancyType(type);
        discrepancy.setExpectedQuantity(expected);
        discrepancy.setReceivedQuantity(received);
        discrepancy.setDifference(difference);
        discrepancy.setStatus(DiscrepancyStatus.PENDING_RESOLUTION);

        log.info("Creating {} discrepancy for transfer {} item {}, difference: {}",
            type, transfer.getId(), item.getId(), difference);

        return discrepancyRepository.save(discrepancy);
    }

    public List<NewTransferDiscrepancy> findByTransferId(UUID transferId) {
        return discrepancyRepository.findByTransferId(transferId);
    }

    public List<NewTransferDiscrepancy> findPendingByTenantId(UUID tenantId) {
        return discrepancyRepository.findPendingByTenantId(tenantId);
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.stockshift.service.transfer.DiscrepancyServiceTest" --info`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/DiscrepancyService.java \
        src/test/java/br/com/stockshift/service/transfer/DiscrepancyServiceTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add DiscrepancyService for evaluating and creating discrepancies

Implements ValidationResult record for evaluating items, detecting
SHORTAGE/EXCESS, and creating NewTransferDiscrepancy records.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Update TransferService.completeValidation() to Create Discrepancies

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/transfer/TransferService.java:390-493`
- Test: `src/test/java/br/com/stockshift/service/transfer/TransferServiceTest.java`

**Step 1: Write the failing test for discrepancy creation**

Add to `TransferServiceTest.java`:

```java
@Nested
class CompleteValidation {

    @Test
    void shouldCreateDiscrepancyForShortage() {
        transfer.setStatus(TransferStatus.VALIDATION_IN_PROGRESS);

        TransferItem item = new TransferItem();
        item.setId(UUID.randomUUID());
        item.setProduct(product);
        item.setSourceBatch(sourceBatch);
        item.setExpectedQuantity(new BigDecimal("50"));
        item.setReceivedQuantity(new BigDecimal("40")); // Shortage of 10
        item.setStatus(TransferItemStatus.PARTIAL);
        transfer.setItems(List.of(item));

        Warehouse destWarehouse = new Warehouse();
        destWarehouse.setId(destinationWarehouseId);
        destWarehouse.setCode("WH02");
        transfer.setDestinationWarehouse(destWarehouse);

        Batch destBatch = new Batch();
        destBatch.setId(UUID.randomUUID());
        destBatch.setQuantity(BigDecimal.ZERO);

        TransferInTransit inTransit = new TransferInTransit();
        inTransit.setQuantity(new BigDecimal("50"));

        when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
        when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
        doNothing().when(securityService).validateAction(any(), any());
        when(batchRepository.findByWarehouseIdAndBatchCode(any(), any())).thenReturn(Optional.of(destBatch));
        when(transferInTransitRepository.findByTransferItemId(any())).thenReturn(Optional.of(inTransit));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Mock discrepancy service
        when(discrepancyService.evaluateItem(any())).thenReturn(
            new DiscrepancyService.ValidationResult(true, DiscrepancyType.SHORTAGE, new BigDecimal("10"))
        );

        Transfer result = transferService.completeValidation(transferId, user);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED_WITH_DISCREPANCY);
        verify(discrepancyService).createDiscrepancy(eq(transfer), eq(item), eq(DiscrepancyType.SHORTAGE));
    }

    @Test
    void shouldNotCreateDiscrepancyWhenQuantitiesMatch() {
        transfer.setStatus(TransferStatus.VALIDATION_IN_PROGRESS);

        TransferItem item = new TransferItem();
        item.setId(UUID.randomUUID());
        item.setProduct(product);
        item.setSourceBatch(sourceBatch);
        item.setExpectedQuantity(new BigDecimal("50"));
        item.setReceivedQuantity(new BigDecimal("50")); // Exact match
        item.setStatus(TransferItemStatus.RECEIVED);
        transfer.setItems(List.of(item));

        Warehouse destWarehouse = new Warehouse();
        destWarehouse.setId(destinationWarehouseId);
        destWarehouse.setCode("WH02");
        transfer.setDestinationWarehouse(destWarehouse);

        Batch destBatch = new Batch();
        destBatch.setId(UUID.randomUUID());
        destBatch.setQuantity(BigDecimal.ZERO);

        TransferInTransit inTransit = new TransferInTransit();
        inTransit.setQuantity(new BigDecimal("50"));

        when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
        when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
        doNothing().when(securityService).validateAction(any(), any());
        when(batchRepository.findByWarehouseIdAndBatchCode(any(), any())).thenReturn(Optional.of(destBatch));
        when(transferInTransitRepository.findByTransferItemId(any())).thenReturn(Optional.of(inTransit));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(discrepancyService.evaluateItem(any())).thenReturn(
            new DiscrepancyService.ValidationResult(false, null, BigDecimal.ZERO)
        );

        Transfer result = transferService.completeValidation(transferId, user);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        verify(discrepancyService, never()).createDiscrepancy(any(), any(), any());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.service.transfer.TransferServiceTest.CompleteValidation" --info`
Expected: FAIL (missing discrepancyService mock and integration)

**Step 3: Update TransferService constructor and completeValidation method**

Add to TransferService constructor:
```java
private final DiscrepancyService discrepancyService;
```

Update `completeValidation()` method (lines 390-493):

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public Transfer completeValidation(UUID transferId, User user) {
    log.info("Completing validation for transfer {} by user {}", transferId, user.getId());

    Transfer transfer = getTransferForUpdate(transferId);

    // Idempotency
    if (transfer.getStatus() == TransferStatus.COMPLETED ||
        transfer.getStatus() == TransferStatus.COMPLETED_WITH_DISCREPANCY) {
        log.info("Transfer {} already completed, returning existing state", transferId);
        return transfer;
    }

    // Validate status
    if (transfer.getStatus() != TransferStatus.VALIDATION_IN_PROGRESS) {
        throw new InvalidTransferStateException(
            "Cannot complete validation for transfer in status " + transfer.getStatus()
        );
    }

    // Validate action
    securityService.validateAction(transfer, TransferAction.COMPLETE);

    boolean hasDiscrepancy = false;

    // Process each item
    for (TransferItem item : transfer.getItems()) {
        BigDecimal received = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : BigDecimal.ZERO;

        if (received.compareTo(BigDecimal.ZERO) <= 0) {
            item.setStatus(TransferItemStatus.MISSING);
            hasDiscrepancy = true;

            // Create discrepancy record for missing item
            discrepancyService.createDiscrepancy(transfer, item, DiscrepancyType.SHORTAGE);
            continue;
        }

        // Create or find destination batch (mirror)
        Batch destinationBatch = resolveDestinationBatch(transfer, item);

        // Record TRANSFER_IN
        InventoryLedger inEntry = new InventoryLedger();
        inEntry.setTenantId(transfer.getTenantId());
        inEntry.setWarehouseId(transfer.getDestinationWarehouse().getId());
        inEntry.setProductId(item.getProduct().getId());
        inEntry.setBatchId(destinationBatch.getId());
        inEntry.setEntryType(LedgerEntryType.TRANSFER_IN);
        inEntry.setQuantity(received);
        inEntry.setBalanceAfter(destinationBatch.getQuantity().add(received));
        inEntry.setReferenceType("TRANSFER");
        inEntry.setReferenceId(transfer.getId());
        inEntry.setTransferItemId(item.getId());
        inEntry.setCreatedBy(user.getId());
        inventoryLedgerRepository.save(inEntry);

        // Record TRANSFER_TRANSIT_CONSUMED
        InventoryLedger consumedEntry = new InventoryLedger();
        consumedEntry.setTenantId(transfer.getTenantId());
        consumedEntry.setWarehouseId(null);
        consumedEntry.setProductId(item.getProduct().getId());
        consumedEntry.setBatchId(null);
        consumedEntry.setEntryType(LedgerEntryType.TRANSFER_TRANSIT_CONSUMED);
        consumedEntry.setQuantity(received);
        consumedEntry.setBalanceAfter(null);
        consumedEntry.setReferenceType("TRANSFER");
        consumedEntry.setReferenceId(transfer.getId());
        consumedEntry.setTransferItemId(item.getId());
        consumedEntry.setCreatedBy(user.getId());
        inventoryLedgerRepository.save(consumedEntry);

        // Update destination batch
        destinationBatch.setQuantity(destinationBatch.getQuantity().add(received));
        batchRepository.save(destinationBatch);

        // Update TransferInTransit
        TransferInTransit inTransit = transferInTransitRepository.findByTransferItemId(item.getId())
            .orElseThrow(() -> new ResourceNotFoundException("TransferInTransit not found for item: " + item.getId()));
        inTransit.setQuantity(inTransit.getQuantity().subtract(received));
        if (inTransit.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            inTransit.setConsumedAt(LocalDateTime.now());
        }
        transferInTransitRepository.save(inTransit);

        // Link destination batch to item
        item.setDestinationBatch(destinationBatch);

        // Evaluate discrepancy using DiscrepancyService
        DiscrepancyService.ValidationResult validationResult = discrepancyService.evaluateItem(item);
        if (validationResult.hasDiscrepancy()) {
            hasDiscrepancy = true;
            discrepancyService.createDiscrepancy(transfer, item, validationResult.discrepancyType());
        }
    }

    // Determine final status
    TransferStatus finalStatus = hasDiscrepancy
        ? TransferStatus.COMPLETED_WITH_DISCREPANCY
        : TransferStatus.COMPLETED;

    transfer.setStatus(finalStatus);
    transfer.setCompletedBy(user);
    transfer.setCompletedAt(LocalDateTime.now());

    transfer = transferRepository.save(transfer);
    log.info("Transfer {} completed with status {}", transferId, finalStatus);

    return transfer;
}
```

**Step 4: Update TransferService constructor**

Add DiscrepancyService to constructor:

```java
public TransferService(
    TransferRepository transferRepository,
    TransferSecurityService securityService,
    TransferStateMachine stateMachine,
    WarehouseAccessService warehouseAccessService,
    WarehouseRepository warehouseRepository,
    ProductRepository productRepository,
    BatchRepository batchRepository,
    InventoryLedgerRepository inventoryLedgerRepository,
    TransferInTransitRepository transferInTransitRepository,
    DiscrepancyService discrepancyService  // Add this
) {
    this.transferRepository = transferRepository;
    this.securityService = securityService;
    this.stateMachine = stateMachine;
    this.warehouseAccessService = warehouseAccessService;
    this.warehouseRepository = warehouseRepository;
    this.productRepository = productRepository;
    this.batchRepository = batchRepository;
    this.inventoryLedgerRepository = inventoryLedgerRepository;
    this.transferInTransitRepository = transferInTransitRepository;
    this.discrepancyService = discrepancyService;  // Add this
}
```

**Step 5: Update test setUp to include discrepancyService mock**

```java
@Mock
private DiscrepancyService discrepancyService;

// In setUp():
transferService = new TransferService(
    transferRepository,
    securityService,
    stateMachine,
    warehouseAccessService,
    warehouseRepository,
    productRepository,
    batchRepository,
    inventoryLedgerRepository,
    transferInTransitRepository,
    discrepancyService  // Add this
);
```

**Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.stockshift.service.transfer.TransferServiceTest" --info`
Expected: PASS

**Step 7: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferService.java \
        src/test/java/br/com/stockshift/service/transfer/TransferServiceTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): integrate DiscrepancyService into completeValidation

TransferService now creates NewTransferDiscrepancy records when
validation completes with quantity mismatches. Uses DiscrepancyService
to evaluate items and create proper discrepancy records.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Create ResolveDiscrepancyRequest DTO

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/transfer/ResolveDiscrepancyRequest.java`
- Test: `src/test/java/br/com/stockshift/dto/transfer/ResolveDiscrepancyRequestTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.DiscrepancyResolution;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResolveDiscrepancyRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void shouldAcceptValidRequest() {
        ResolveDiscrepancyRequest request = new ResolveDiscrepancyRequest();
        request.setResolution(DiscrepancyResolution.WRITE_OFF);
        request.setJustification("Damaged during transport");

        var violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectMissingResolution() {
        ResolveDiscrepancyRequest request = new ResolveDiscrepancyRequest();
        request.setJustification("Some reason");

        var violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("resolution");
    }

    @Test
    void shouldRejectMissingJustification() {
        ResolveDiscrepancyRequest request = new ResolveDiscrepancyRequest();
        request.setResolution(DiscrepancyResolution.WRITE_OFF);

        var violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("justification");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.dto.transfer.ResolveDiscrepancyRequestTest" --info`
Expected: FAIL with "cannot find symbol: class ResolveDiscrepancyRequest"

**Step 3: Create the DTO**

```java
package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.DiscrepancyResolution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResolveDiscrepancyRequest {

    @NotNull(message = "Resolution is required")
    private DiscrepancyResolution resolution;

    @NotBlank(message = "Justification is required")
    private String justification;
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.stockshift.dto.transfer.ResolveDiscrepancyRequestTest" --info`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/transfer/ResolveDiscrepancyRequest.java \
        src/test/java/br/com/stockshift/dto/transfer/ResolveDiscrepancyRequestTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add ResolveDiscrepancyRequest DTO

Add DTO for discrepancy resolution with required resolution type
and justification fields with validation.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Add resolveDiscrepancy Method to DiscrepancyService

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/transfer/DiscrepancyService.java`
- Test: `src/test/java/br/com/stockshift/service/transfer/DiscrepancyServiceTest.java`

**Step 1: Write the failing test**

Add to `DiscrepancyServiceTest.java`:

```java
@Mock
private InventoryLedgerRepository inventoryLedgerRepository;

@Mock
private TransferInTransitRepository transferInTransitRepository;

// Update setUp to include new dependencies
@BeforeEach
void setUp() {
    discrepancyService = new DiscrepancyService(
        discrepancyRepository,
        inventoryLedgerRepository,
        transferInTransitRepository
    );
    // ... rest of setup
}

@Nested
class ResolveDiscrepancy {

    private NewTransferDiscrepancy discrepancy;
    private User resolver;
    private TransferInTransit inTransit;

    @BeforeEach
    void setUp() {
        resolver = new User();
        resolver.setId(UUID.randomUUID());

        discrepancy = new NewTransferDiscrepancy();
        discrepancy.setId(UUID.randomUUID());
        discrepancy.setTenantId(tenantId);
        discrepancy.setTransfer(transfer);
        discrepancy.setTransferItem(item);
        discrepancy.setDiscrepancyType(DiscrepancyType.SHORTAGE);
        discrepancy.setExpectedQuantity(new BigDecimal("50"));
        discrepancy.setReceivedQuantity(new BigDecimal("40"));
        discrepancy.setDifference(new BigDecimal("10"));
        discrepancy.setStatus(DiscrepancyStatus.PENDING_RESOLUTION);

        inTransit = new TransferInTransit();
        inTransit.setId(UUID.randomUUID());
        inTransit.setQuantity(new BigDecimal("10"));
    }

    @Test
    void shouldResolveWithWriteOff() {
        when(discrepancyRepository.findById(discrepancy.getId())).thenReturn(Optional.of(discrepancy));
        when(transferInTransitRepository.findByTransferItemId(item.getId())).thenReturn(Optional.of(inTransit));
        when(discrepancyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NewTransferDiscrepancy result = discrepancyService.resolveDiscrepancy(
            discrepancy.getId(),
            DiscrepancyResolution.WRITE_OFF,
            "Damaged in transport",
            resolver
        );

        assertThat(result.getStatus()).isEqualTo(DiscrepancyStatus.WRITTEN_OFF);
        assertThat(result.getResolution()).isEqualTo(DiscrepancyResolution.WRITE_OFF);
        assertThat(result.getResolvedBy()).isEqualTo(resolver);
        assertThat(result.getResolvedAt()).isNotNull();

        // Verify TRANSFER_LOSS ledger entry was created
        verify(inventoryLedgerRepository).save(argThat(ledger ->
            ledger.getEntryType() == LedgerEntryType.TRANSFER_LOSS &&
            ledger.getQuantity().compareTo(new BigDecimal("10")) == 0
        ));

        // Verify transit was consumed
        assertThat(inTransit.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(inTransit.getConsumedAt()).isNotNull();
    }

    @Test
    void shouldRejectResolvingAlreadyResolved() {
        discrepancy.setStatus(DiscrepancyStatus.RESOLVED);
        when(discrepancyRepository.findById(discrepancy.getId())).thenReturn(Optional.of(discrepancy));

        assertThatThrownBy(() -> discrepancyService.resolveDiscrepancy(
            discrepancy.getId(),
            DiscrepancyResolution.WRITE_OFF,
            "reason",
            resolver
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("already resolved");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.service.transfer.DiscrepancyServiceTest.ResolveDiscrepancy" --info`
Expected: FAIL with missing method or wrong constructor

**Step 3: Update DiscrepancyService with resolveDiscrepancy method**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.*;
import br.com.stockshift.repository.InventoryLedgerRepository;
import br.com.stockshift.repository.NewTransferDiscrepancyRepository;
import br.com.stockshift.repository.TransferInTransitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscrepancyService {

    private final NewTransferDiscrepancyRepository discrepancyRepository;
    private final InventoryLedgerRepository inventoryLedgerRepository;
    private final TransferInTransitRepository transferInTransitRepository;

    public record ValidationResult(
        boolean hasDiscrepancy,
        DiscrepancyType discrepancyType,
        BigDecimal difference
    ) {}

    public ValidationResult evaluateItem(TransferItem item) {
        BigDecimal expected = item.getExpectedQuantity();
        BigDecimal received = item.getReceivedQuantity() != null
            ? item.getReceivedQuantity()
            : BigDecimal.ZERO;

        int comparison = received.compareTo(expected);

        if (comparison == 0) {
            return new ValidationResult(false, null, BigDecimal.ZERO);
        } else if (comparison < 0) {
            BigDecimal difference = expected.subtract(received);
            return new ValidationResult(true, DiscrepancyType.SHORTAGE, difference);
        } else {
            BigDecimal difference = received.subtract(expected);
            return new ValidationResult(true, DiscrepancyType.EXCESS, difference);
        }
    }

    public NewTransferDiscrepancy createDiscrepancy(
        Transfer transfer,
        TransferItem item,
        DiscrepancyType type
    ) {
        BigDecimal expected = item.getExpectedQuantity();
        BigDecimal received = item.getReceivedQuantity() != null
            ? item.getReceivedQuantity()
            : BigDecimal.ZERO;
        BigDecimal difference = type == DiscrepancyType.SHORTAGE
            ? expected.subtract(received)
            : received.subtract(expected);

        NewTransferDiscrepancy discrepancy = new NewTransferDiscrepancy();
        discrepancy.setTenantId(transfer.getTenantId());
        discrepancy.setTransfer(transfer);
        discrepancy.setTransferItem(item);
        discrepancy.setDiscrepancyType(type);
        discrepancy.setExpectedQuantity(expected);
        discrepancy.setReceivedQuantity(received);
        discrepancy.setDifference(difference);
        discrepancy.setStatus(DiscrepancyStatus.PENDING_RESOLUTION);

        log.info("Creating {} discrepancy for transfer {} item {}, difference: {}",
            type, transfer.getId(), item.getId(), difference);

        return discrepancyRepository.save(discrepancy);
    }

    @Transactional
    public NewTransferDiscrepancy resolveDiscrepancy(
        UUID discrepancyId,
        DiscrepancyResolution resolution,
        String justification,
        User resolver
    ) {
        NewTransferDiscrepancy discrepancy = discrepancyRepository.findById(discrepancyId)
            .orElseThrow(() -> new ResourceNotFoundException("Discrepancy not found: " + discrepancyId));

        if (discrepancy.getStatus() != DiscrepancyStatus.PENDING_RESOLUTION) {
            throw new BusinessException("Discrepancy is already resolved");
        }

        log.info("Resolving discrepancy {} with {} by user {}",
            discrepancyId, resolution, resolver.getId());

        switch (resolution) {
            case WRITE_OFF -> handleWriteOff(discrepancy, resolver);
            case FOUND -> handleFound(discrepancy, resolver);
            case ACCEPTED -> handleAccepted(discrepancy, resolver);
            case RETURN_TRANSIT -> handleReturnTransit(discrepancy, resolver);
        }

        discrepancy.setResolution(resolution);
        discrepancy.setResolutionNotes(justification);
        discrepancy.setResolvedBy(resolver);
        discrepancy.setResolvedAt(LocalDateTime.now());

        if (resolution == DiscrepancyResolution.WRITE_OFF) {
            discrepancy.setStatus(DiscrepancyStatus.WRITTEN_OFF);
        } else {
            discrepancy.setStatus(DiscrepancyStatus.RESOLVED);
        }

        return discrepancyRepository.save(discrepancy);
    }

    private void handleWriteOff(NewTransferDiscrepancy discrepancy, User resolver) {
        Transfer transfer = discrepancy.getTransfer();
        TransferItem item = discrepancy.getTransferItem();
        BigDecimal lossQuantity = discrepancy.getDifference();

        // Create TRANSFER_LOSS ledger entry
        InventoryLedger lossEntry = new InventoryLedger();
        lossEntry.setTenantId(transfer.getTenantId());
        lossEntry.setWarehouseId(null); // Virtual
        lossEntry.setProductId(item.getProduct().getId());
        lossEntry.setBatchId(null);
        lossEntry.setEntryType(LedgerEntryType.TRANSFER_LOSS);
        lossEntry.setQuantity(lossQuantity);
        lossEntry.setBalanceAfter(null);
        lossEntry.setReferenceType("TRANSFER_DISCREPANCY");
        lossEntry.setReferenceId(discrepancy.getId());
        lossEntry.setTransferItemId(item.getId());
        lossEntry.setCreatedBy(resolver.getId());
        lossEntry.setNotes("Write-off for transfer discrepancy");
        inventoryLedgerRepository.save(lossEntry);

        // Consume remaining transit
        TransferInTransit inTransit = transferInTransitRepository.findByTransferItemId(item.getId())
            .orElse(null);
        if (inTransit != null && inTransit.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            inTransit.setQuantity(BigDecimal.ZERO);
            inTransit.setConsumedAt(LocalDateTime.now());
            transferInTransitRepository.save(inTransit);
        }

        log.info("Written off {} units for discrepancy {}", lossQuantity, discrepancy.getId());
    }

    private void handleFound(NewTransferDiscrepancy discrepancy, User resolver) {
        // FOUND means the item was located - requires manual adjustment
        // For now, just mark as resolved. Manual ADJUSTMENT_IN can be done separately.
        log.info("Discrepancy {} marked as FOUND - manual adjustment may be needed", discrepancy.getId());
    }

    private void handleAccepted(NewTransferDiscrepancy discrepancy, User resolver) {
        // ACCEPTED is for EXCESS type - just mark as resolved with audit flag
        log.info("Excess discrepancy {} accepted", discrepancy.getId());
    }

    private void handleReturnTransit(NewTransferDiscrepancy discrepancy, User resolver) {
        // RETURN_TRANSIT would create a reverse transfer - future implementation
        log.info("Return transit for discrepancy {} - future implementation", discrepancy.getId());
    }

    public List<NewTransferDiscrepancy> findByTransferId(UUID transferId) {
        return discrepancyRepository.findByTransferId(transferId);
    }

    public List<NewTransferDiscrepancy> findPendingByTenantId(UUID tenantId) {
        return discrepancyRepository.findPendingByTenantId(tenantId);
    }

    public NewTransferDiscrepancy findById(UUID id) {
        return discrepancyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Discrepancy not found: " + id));
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.stockshift.service.transfer.DiscrepancyServiceTest" --info`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/DiscrepancyService.java \
        src/test/java/br/com/stockshift/service/transfer/DiscrepancyServiceTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add discrepancy resolution with TRANSFER_LOSS ledger

Implement resolveDiscrepancy() method with WRITE_OFF creating
TRANSFER_LOSS ledger entries and consuming remaining transit.
Add handlers for FOUND, ACCEPTED, and RETURN_TRANSIT resolutions.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Add Discrepancy Endpoints to TransferController

**Files:**
- Modify: `src/main/java/br/com/stockshift/controller/TransferController.java`
- Test: `src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java`

**Step 1: Write the failing test**

Add to existing integration test file or create new test:

```java
@Nested
class DiscrepancyEndpoints {

    @Test
    void shouldListDiscrepanciesForTransfer() throws Exception {
        // Setup transfer with discrepancy
        UUID transferId = createTransferWithDiscrepancy();

        mockMvc.perform(get("/stockshift/transfers/{id}/discrepancies", transferId)
                .header("Authorization", "Bearer " + getAuthToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].discrepancyType").value("SHORTAGE"));
    }

    @Test
    void shouldResolveDiscrepancy() throws Exception {
        UUID discrepancyId = createPendingDiscrepancy();

        mockMvc.perform(post("/stockshift/transfers/discrepancies/{id}/resolve", discrepancyId)
                .header("Authorization", "Bearer " + getAuthToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "resolution": "WRITE_OFF",
                        "justification": "Damaged during transport"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WRITTEN_OFF"))
            .andExpect(jsonPath("$.data.resolution").value("WRITE_OFF"));
    }

    @Test
    void shouldRejectResolvingAlreadyResolved() throws Exception {
        UUID discrepancyId = createResolvedDiscrepancy();

        mockMvc.perform(post("/stockshift/transfers/discrepancies/{id}/resolve", discrepancyId)
                .header("Authorization", "Bearer " + getAuthToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "resolution": "WRITE_OFF",
                        "justification": "Trying again"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.controller.TransferControllerIntegrationTest.DiscrepancyEndpoints" --info`
Expected: FAIL with 404 (endpoint doesn't exist)

**Step 3: Add endpoints to TransferController**

```java
// Add to TransferController.java

@Autowired
private DiscrepancyService discrepancyService;

@GetMapping("/{id}/discrepancies")
public ApiResponse<List<DiscrepancyResponse>> getDiscrepancies(
    @PathVariable UUID id,
    @AuthenticationPrincipal User user
) {
    log.info("Getting discrepancies for transfer {} by user {}", id, user.getId());

    // Validate access to transfer
    Transfer transfer = transferService.getTransfer(id);

    List<NewTransferDiscrepancy> discrepancies = discrepancyService.findByTransferId(id);
    List<DiscrepancyResponse> responses = discrepancies.stream()
        .map(this::toDiscrepancyResponse)
        .toList();

    return ApiResponse.success(responses);
}

@PostMapping("/discrepancies/{discrepancyId}/resolve")
public ApiResponse<DiscrepancyResponse> resolveDiscrepancy(
    @PathVariable UUID discrepancyId,
    @Valid @RequestBody ResolveDiscrepancyRequest request,
    @AuthenticationPrincipal User user
) {
    log.info("Resolving discrepancy {} with {} by user {}",
        discrepancyId, request.getResolution(), user.getId());

    NewTransferDiscrepancy resolved = discrepancyService.resolveDiscrepancy(
        discrepancyId,
        request.getResolution(),
        request.getJustification(),
        user
    );

    return ApiResponse.success(toDiscrepancyResponse(resolved));
}

private DiscrepancyResponse toDiscrepancyResponse(NewTransferDiscrepancy discrepancy) {
    return new DiscrepancyResponse(
        discrepancy.getId(),
        discrepancy.getTransfer().getId(),
        discrepancy.getTransferItem().getId(),
        discrepancy.getDiscrepancyType(),
        discrepancy.getExpectedQuantity(),
        discrepancy.getReceivedQuantity(),
        discrepancy.getDifference(),
        discrepancy.getStatus(),
        discrepancy.getResolution(),
        discrepancy.getResolutionNotes(),
        discrepancy.getResolvedBy() != null ? discrepancy.getResolvedBy().getId() : null,
        discrepancy.getResolvedAt(),
        discrepancy.getCreatedAt()
    );
}
```

**Step 4: Create DiscrepancyResponse DTO**

```java
package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.DiscrepancyResolution;
import br.com.stockshift.model.enums.DiscrepancyStatus;
import br.com.stockshift.model.enums.DiscrepancyType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DiscrepancyResponse(
    UUID id,
    UUID transferId,
    UUID transferItemId,
    DiscrepancyType discrepancyType,
    BigDecimal expectedQuantity,
    BigDecimal receivedQuantity,
    BigDecimal difference,
    DiscrepancyStatus status,
    DiscrepancyResolution resolution,
    String resolutionNotes,
    UUID resolvedBy,
    LocalDateTime resolvedAt,
    LocalDateTime createdAt
) {}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.stockshift.controller.TransferControllerIntegrationTest.DiscrepancyEndpoints" --info`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/TransferController.java \
        src/main/java/br/com/stockshift/dto/transfer/DiscrepancyResponse.java \
        src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add discrepancy list and resolve endpoints

Add GET /transfers/{id}/discrepancies and
POST /transfers/discrepancies/{id}/resolve endpoints.
Create DiscrepancyResponse DTO.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Add Ledger Query Endpoints

**Files:**
- Modify: `src/main/java/br/com/stockshift/controller/TransferController.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/LedgerEntryResponse.java`
- Create: `src/main/java/br/com/stockshift/service/LedgerQueryService.java`
- Test: `src/test/java/br/com/stockshift/service/LedgerQueryServiceTest.java`

**Step 1: Write the failing test for LedgerQueryService**

```java
package br.com.stockshift.service;

import br.com.stockshift.model.entity.InventoryLedger;
import br.com.stockshift.model.enums.LedgerEntryType;
import br.com.stockshift.repository.InventoryLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerQueryServiceTest {

    @Mock
    private InventoryLedgerRepository ledgerRepository;

    private LedgerQueryService ledgerQueryService;

    @BeforeEach
    void setUp() {
        ledgerQueryService = new LedgerQueryService(ledgerRepository);
    }

    @Test
    void shouldFindLedgerEntriesByTransferId() {
        UUID transferId = UUID.randomUUID();

        InventoryLedger entry1 = new InventoryLedger();
        entry1.setId(UUID.randomUUID());
        entry1.setEntryType(LedgerEntryType.TRANSFER_OUT);
        entry1.setQuantity(new BigDecimal("50"));

        InventoryLedger entry2 = new InventoryLedger();
        entry2.setId(UUID.randomUUID());
        entry2.setEntryType(LedgerEntryType.TRANSFER_IN_TRANSIT);
        entry2.setQuantity(new BigDecimal("50"));

        when(ledgerRepository.findByReferenceTypeAndReferenceId("TRANSFER", transferId))
            .thenReturn(List.of(entry1, entry2));

        List<InventoryLedger> result = ledgerQueryService.findByTransferId(transferId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEntryType()).isEqualTo(LedgerEntryType.TRANSFER_OUT);
    }

    @Test
    void shouldFindLedgerEntriesByBatchId() {
        UUID batchId = UUID.randomUUID();

        when(ledgerRepository.findByBatchIdOrderByCreatedAtDesc(batchId))
            .thenReturn(List.of());

        List<InventoryLedger> result = ledgerQueryService.findByBatchId(batchId);

        assertThat(result).isNotNull();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.service.LedgerQueryServiceTest" --info`
Expected: FAIL with "cannot find symbol: class LedgerQueryService"

**Step 3: Create LedgerQueryService**

```java
package br.com.stockshift.service;

import br.com.stockshift.model.entity.InventoryLedger;
import br.com.stockshift.repository.InventoryLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerQueryService {

    private final InventoryLedgerRepository ledgerRepository;

    public List<InventoryLedger> findByTransferId(UUID transferId) {
        return ledgerRepository.findByReferenceTypeAndReferenceId("TRANSFER", transferId);
    }

    public List<InventoryLedger> findByBatchId(UUID batchId) {
        return ledgerRepository.findByBatchIdOrderByCreatedAtDesc(batchId);
    }

    public List<InventoryLedger> findByWarehouseId(UUID warehouseId) {
        return ledgerRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId);
    }

    public List<InventoryLedger> findByProductId(UUID productId) {
        return ledgerRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }
}
```

**Step 4: Add repository methods**

Add to `InventoryLedgerRepository.java`:

```java
List<InventoryLedger> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

List<InventoryLedger> findByBatchIdOrderByCreatedAtDesc(UUID batchId);

List<InventoryLedger> findByWarehouseIdOrderByCreatedAtDesc(UUID warehouseId);

List<InventoryLedger> findByProductIdOrderByCreatedAtDesc(UUID productId);
```

**Step 5: Create LedgerEntryResponse DTO**

```java
package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.LedgerEntryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
    UUID id,
    UUID warehouseId,
    UUID productId,
    UUID batchId,
    LedgerEntryType entryType,
    boolean isDebit,
    BigDecimal quantity,
    BigDecimal balanceAfter,
    String referenceType,
    UUID referenceId,
    UUID transferItemId,
    String notes,
    UUID createdBy,
    LocalDateTime createdAt
) {}
```

**Step 6: Add endpoint to TransferController**

```java
@Autowired
private LedgerQueryService ledgerQueryService;

@GetMapping("/{id}/ledger")
public ApiResponse<List<LedgerEntryResponse>> getLedgerEntries(
    @PathVariable UUID id,
    @AuthenticationPrincipal User user
) {
    log.info("Getting ledger entries for transfer {} by user {}", id, user.getId());

    // Validate access to transfer
    Transfer transfer = transferService.getTransfer(id);

    List<InventoryLedger> entries = ledgerQueryService.findByTransferId(id);
    List<LedgerEntryResponse> responses = entries.stream()
        .map(this::toLedgerEntryResponse)
        .toList();

    return ApiResponse.success(responses);
}

private LedgerEntryResponse toLedgerEntryResponse(InventoryLedger entry) {
    return new LedgerEntryResponse(
        entry.getId(),
        entry.getWarehouseId(),
        entry.getProductId(),
        entry.getBatchId(),
        entry.getEntryType(),
        entry.getEntryType().isDebit(),
        entry.getQuantity(),
        entry.getBalanceAfter(),
        entry.getReferenceType(),
        entry.getReferenceId(),
        entry.getTransferItemId(),
        entry.getNotes(),
        entry.getCreatedBy(),
        entry.getCreatedAt()
    );
}
```

**Step 7: Run tests**

Run: `./gradlew test --tests "br.com.stockshift.service.LedgerQueryServiceTest" --info`
Expected: PASS

**Step 8: Commit**

```bash
git add src/main/java/br/com/stockshift/service/LedgerQueryService.java \
        src/main/java/br/com/stockshift/dto/transfer/LedgerEntryResponse.java \
        src/main/java/br/com/stockshift/repository/InventoryLedgerRepository.java \
        src/main/java/br/com/stockshift/controller/TransferController.java \
        src/test/java/br/com/stockshift/service/LedgerQueryServiceTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add ledger query service and endpoint

Add LedgerQueryService for querying ledger by transfer, batch,
warehouse, product. Add GET /transfers/{id}/ledger endpoint
with LedgerEntryResponse DTO.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Create Batch Quantity Reconciliation Service

**Files:**
- Create: `src/main/java/br/com/stockshift/service/ReconciliationService.java`
- Create: `src/main/java/br/com/stockshift/dto/ReconciliationResult.java`
- Test: `src/test/java/br/com/stockshift/service/ReconciliationServiceTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.ReconciliationResult;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.InventoryLedger;
import br.com.stockshift.model.enums.LedgerEntryType;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.InventoryLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private InventoryLedgerRepository ledgerRepository;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(batchRepository, ledgerRepository);
    }

    @Test
    void shouldDetectNoDiscrepancyWhenBalanced() {
        UUID tenantId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        Batch batch = new Batch();
        batch.setId(batchId);
        batch.setQuantity(new BigDecimal("50"));

        InventoryLedger entry1 = new InventoryLedger();
        entry1.setEntryType(LedgerEntryType.PURCHASE_IN);
        entry1.setQuantity(new BigDecimal("100"));

        InventoryLedger entry2 = new InventoryLedger();
        entry2.setEntryType(LedgerEntryType.TRANSFER_OUT);
        entry2.setQuantity(new BigDecimal("50"));

        when(batchRepository.findAllByTenantId(tenantId)).thenReturn(List.of(batch));
        when(ledgerRepository.findByBatchId(batchId)).thenReturn(List.of(entry1, entry2));

        List<ReconciliationResult> results = reconciliationService.reconcileTenant(tenantId);

        assertThat(results).isEmpty();
    }

    @Test
    void shouldDetectDiscrepancyWhenUnbalanced() {
        UUID tenantId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        Batch batch = new Batch();
        batch.setId(batchId);
        batch.setBatchCode("BATCH-001");
        batch.setQuantity(new BigDecimal("60")); // Wrong!

        InventoryLedger entry1 = new InventoryLedger();
        entry1.setEntryType(LedgerEntryType.PURCHASE_IN);
        entry1.setQuantity(new BigDecimal("100"));

        InventoryLedger entry2 = new InventoryLedger();
        entry2.setEntryType(LedgerEntryType.TRANSFER_OUT);
        entry2.setQuantity(new BigDecimal("50"));

        when(batchRepository.findAllByTenantId(tenantId)).thenReturn(List.of(batch));
        when(ledgerRepository.findByBatchId(batchId)).thenReturn(List.of(entry1, entry2));

        List<ReconciliationResult> results = reconciliationService.reconcileTenant(tenantId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).batchId()).isEqualTo(batchId);
        assertThat(results.get(0).materializedQuantity()).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(results.get(0).calculatedQuantity()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(results.get(0).difference()).isEqualByComparingTo(new BigDecimal("10"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.service.ReconciliationServiceTest" --info`
Expected: FAIL with "cannot find symbol: class ReconciliationService"

**Step 3: Create ReconciliationResult DTO**

```java
package br.com.stockshift.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReconciliationResult(
    UUID batchId,
    String batchCode,
    BigDecimal materializedQuantity,
    BigDecimal calculatedQuantity,
    BigDecimal difference
) {}
```

**Step 4: Create ReconciliationService**

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.ReconciliationResult;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.InventoryLedger;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.InventoryLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final BatchRepository batchRepository;
    private final InventoryLedgerRepository ledgerRepository;

    public List<ReconciliationResult> reconcileTenant(UUID tenantId) {
        log.info("Starting reconciliation for tenant {}", tenantId);

        List<Batch> batches = batchRepository.findAllByTenantId(tenantId);
        List<ReconciliationResult> discrepancies = new ArrayList<>();

        for (Batch batch : batches) {
            BigDecimal calculatedQuantity = calculateQuantityFromLedger(batch.getId());
            BigDecimal materializedQuantity = batch.getQuantity();

            if (calculatedQuantity.compareTo(materializedQuantity) != 0) {
                BigDecimal difference = materializedQuantity.subtract(calculatedQuantity);
                discrepancies.add(new ReconciliationResult(
                    batch.getId(),
                    batch.getBatchCode(),
                    materializedQuantity,
                    calculatedQuantity,
                    difference
                ));
                log.warn("Discrepancy found in batch {}: materialized={}, calculated={}, diff={}",
                    batch.getBatchCode(), materializedQuantity, calculatedQuantity, difference);
            }
        }

        log.info("Reconciliation complete for tenant {}. Found {} discrepancies",
            tenantId, discrepancies.size());
        return discrepancies;
    }

    public BigDecimal calculateQuantityFromLedger(UUID batchId) {
        List<InventoryLedger> entries = ledgerRepository.findByBatchId(batchId);

        BigDecimal total = BigDecimal.ZERO;
        for (InventoryLedger entry : entries) {
            if (entry.getEntryType().isDebit()) {
                total = total.subtract(entry.getQuantity());
            } else {
                total = total.add(entry.getQuantity());
            }
        }

        return total;
    }

    public ReconciliationResult reconcileBatch(UUID batchId) {
        Batch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return null;
        }

        BigDecimal calculatedQuantity = calculateQuantityFromLedger(batchId);
        BigDecimal materializedQuantity = batch.getQuantity();

        if (calculatedQuantity.compareTo(materializedQuantity) != 0) {
            BigDecimal difference = materializedQuantity.subtract(calculatedQuantity);
            return new ReconciliationResult(
                batch.getId(),
                batch.getBatchCode(),
                materializedQuantity,
                calculatedQuantity,
                difference
            );
        }

        return null;
    }
}
```

**Step 5: Add repository method**

Add to `InventoryLedgerRepository.java`:

```java
List<InventoryLedger> findByBatchId(UUID batchId);
```

Add to `BatchRepository.java`:

```java
List<Batch> findAllByTenantId(UUID tenantId);
```

**Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.stockshift.service.ReconciliationServiceTest" --info`
Expected: PASS

**Step 7: Commit**

```bash
git add src/main/java/br/com/stockshift/service/ReconciliationService.java \
        src/main/java/br/com/stockshift/dto/ReconciliationResult.java \
        src/main/java/br/com/stockshift/repository/InventoryLedgerRepository.java \
        src/main/java/br/com/stockshift/repository/BatchRepository.java \
        src/test/java/br/com/stockshift/service/ReconciliationServiceTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add ReconciliationService for batch quantity verification

Implements reconcileTenant() and reconcileBatch() to compare
materialized batch quantities against ledger-calculated quantities.
Detects discrepancies for audit/investigation.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Create Scheduled Reconciliation Job

**Files:**
- Create: `src/main/java/br/com/stockshift/job/ReconciliationJob.java`
- Create: `src/main/java/br/com/stockshift/service/AlertService.java`
- Test: `src/test/java/br/com/stockshift/job/ReconciliationJobTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.job;

import br.com.stockshift.dto.ReconciliationResult;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.service.AlertService;
import br.com.stockshift.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationJobTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ReconciliationService reconciliationService;

    @Mock
    private AlertService alertService;

    private ReconciliationJob reconciliationJob;

    @BeforeEach
    void setUp() {
        reconciliationJob = new ReconciliationJob(tenantRepository, reconciliationService, alertService);
    }

    @Test
    void shouldRunReconciliationForAllTenants() {
        Tenant tenant1 = new Tenant();
        tenant1.setId(UUID.randomUUID());
        tenant1.setName("Tenant 1");

        Tenant tenant2 = new Tenant();
        tenant2.setId(UUID.randomUUID());
        tenant2.setName("Tenant 2");

        when(tenantRepository.findAll()).thenReturn(List.of(tenant1, tenant2));
        when(reconciliationService.reconcileTenant(any())).thenReturn(List.of());

        reconciliationJob.runDailyReconciliation();

        verify(reconciliationService).reconcileTenant(tenant1.getId());
        verify(reconciliationService).reconcileTenant(tenant2.getId());
        verify(alertService, never()).sendCriticalAlert(any(), any());
    }

    @Test
    void shouldSendAlertWhenDiscrepanciesFound() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Problem Tenant");

        ReconciliationResult discrepancy = new ReconciliationResult(
            UUID.randomUUID(),
            "BATCH-001",
            new BigDecimal("60"),
            new BigDecimal("50"),
            new BigDecimal("10")
        );

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(reconciliationService.reconcileTenant(tenant.getId())).thenReturn(List.of(discrepancy));

        reconciliationJob.runDailyReconciliation();

        verify(alertService).sendCriticalAlert(
            eq("Batch quantity mismatch detected"),
            eq(List.of(discrepancy))
        );
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.job.ReconciliationJobTest" --info`
Expected: FAIL with "cannot find symbol: class ReconciliationJob"

**Step 3: Create AlertService**

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.ReconciliationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AlertService {

    public void sendCriticalAlert(String subject, List<ReconciliationResult> discrepancies) {
        log.error("CRITICAL ALERT: {} - {} discrepancies found", subject, discrepancies.size());
        for (ReconciliationResult discrepancy : discrepancies) {
            log.error("  Batch {}: materialized={}, calculated={}, diff={}",
                discrepancy.batchCode(),
                discrepancy.materializedQuantity(),
                discrepancy.calculatedQuantity(),
                discrepancy.difference()
            );
        }
        // TODO: Integrate with actual alerting system (email, Slack, PagerDuty, etc.)
    }
}
```

**Step 4: Create ReconciliationJob**

```java
package br.com.stockshift.job;

import br.com.stockshift.dto.ReconciliationResult;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.service.AlertService;
import br.com.stockshift.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJob {

    private final TenantRepository tenantRepository;
    private final ReconciliationService reconciliationService;
    private final AlertService alertService;

    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void runDailyReconciliation() {
        log.info("Starting daily batch quantity reconciliation");

        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            try {
                List<ReconciliationResult> discrepancies =
                    reconciliationService.reconcileTenant(tenant.getId());

                if (!discrepancies.isEmpty()) {
                    log.warn("Found {} discrepancies for tenant {}",
                        discrepancies.size(), tenant.getName());
                    alertService.sendCriticalAlert(
                        "Batch quantity mismatch detected",
                        discrepancies
                    );
                }
            } catch (Exception e) {
                log.error("Error reconciling tenant {}: {}", tenant.getId(), e.getMessage(), e);
            }
        }

        log.info("Daily batch quantity reconciliation completed");
    }
}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.stockshift.job.ReconciliationJobTest" --info`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/java/br/com/stockshift/job/ReconciliationJob.java \
        src/main/java/br/com/stockshift/service/AlertService.java \
        src/test/java/br/com/stockshift/job/ReconciliationJobTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add scheduled reconciliation job

Add ReconciliationJob running daily at 2 AM to verify batch
quantities match ledger calculations. Sends critical alerts
via AlertService when discrepancies are found.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Add Integration Test for Full Transfer Flow with Discrepancy

**Files:**
- Modify: `src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java`

**Step 1: Write the integration test**

```java
@Nested
class FullTransferFlowWithDiscrepancy {

    @Test
    @Transactional
    void shouldCompleteTransferWithDiscrepancyAndResolve() throws Exception {
        // 1. Create warehouses
        Warehouse sourceWarehouse = createWarehouse("WH-SOURCE", "Source Warehouse");
        Warehouse destWarehouse = createWarehouse("WH-DEST", "Destination Warehouse");

        // 2. Create product and batch with 100 units
        Product product = createProduct("PROD-001", "Test Product");
        Batch batch = createBatch(product, sourceWarehouse, new BigDecimal("100"));

        // 3. Create transfer for 50 units
        String createResponse = mockMvc.perform(post("/stockshift/transfers")
                .header("Authorization", "Bearer " + sourceUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                        "sourceWarehouseId": "%s",
                        "destinationWarehouseId": "%s",
                        "items": [{
                            "productId": "%s",
                            "batchId": "%s",
                            "quantity": 50
                        }]
                    }
                    """, sourceWarehouse.getId(), destWarehouse.getId(),
                        product.getId(), batch.getId())))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        UUID transferId = extractId(createResponse);

        // 4. Dispatch transfer
        mockMvc.perform(post("/stockshift/transfers/{id}/dispatch", transferId)
                .header("Authorization", "Bearer " + sourceUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));

        // Verify source batch reduced to 50
        assertThat(batchRepository.findById(batch.getId()).get().getQuantity())
            .isEqualByComparingTo(new BigDecimal("50"));

        // 5. Start validation at destination
        mockMvc.perform(post("/stockshift/transfers/{id}/validation/start", transferId)
                .header("Authorization", "Bearer " + destUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("VALIDATION_IN_PROGRESS"));

        // 6. Scan only 40 units (shortage of 10)
        mockMvc.perform(post("/stockshift/transfers/{id}/validation/scan", transferId)
                .header("Authorization", "Bearer " + destUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                        "barcode": "%s",
                        "quantity": 40
                    }
                    """, product.getBarcode())))
            .andExpect(status().isOk());

        // 7. Complete validation
        mockMvc.perform(post("/stockshift/transfers/{id}/validation/complete", transferId)
                .header("Authorization", "Bearer " + destUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED_WITH_DISCREPANCY"));

        // 8. Verify discrepancy was created
        String discrepanciesResponse = mockMvc.perform(
                get("/stockshift/transfers/{id}/discrepancies", transferId)
                    .header("Authorization", "Bearer " + destUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].discrepancyType").value("SHORTAGE"))
            .andExpect(jsonPath("$.data[0].expectedQuantity").value(50))
            .andExpect(jsonPath("$.data[0].receivedQuantity").value(40))
            .andExpect(jsonPath("$.data[0].difference").value(10))
            .andExpect(jsonPath("$.data[0].status").value("PENDING_RESOLUTION"))
            .andReturn().getResponse().getContentAsString();

        UUID discrepancyId = extractFirstDiscrepancyId(discrepanciesResponse);

        // 9. Verify ledger entries
        mockMvc.perform(get("/stockshift/transfers/{id}/ledger", transferId)
                .header("Authorization", "Bearer " + destUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(4)); // OUT, IN_TRANSIT, IN, TRANSIT_CONSUMED

        // 10. Resolve discrepancy as write-off
        mockMvc.perform(post("/stockshift/transfers/discrepancies/{id}/resolve", discrepancyId)
                .header("Authorization", "Bearer " + destUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "resolution": "WRITE_OFF",
                        "justification": "Damaged during transport"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WRITTEN_OFF"))
            .andExpect(jsonPath("$.data.resolution").value("WRITE_OFF"));

        // 11. Verify TRANSFER_LOSS was created in ledger
        mockMvc.perform(get("/stockshift/transfers/{id}/ledger", transferId)
                .header("Authorization", "Bearer " + destUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(5)); // Now includes LOSS

        // 12. Verify final quantities
        // Source: 50 (100 - 50 dispatched)
        assertThat(batchRepository.findById(batch.getId()).get().getQuantity())
            .isEqualByComparingTo(new BigDecimal("50"));

        // Destination: 40 (received)
        Batch destBatch = batchRepository.findByWarehouseIdAndBatchCode(
            destWarehouse.getId(),
            batch.getBatchCode() + "-" + destWarehouse.getCode()
        ).get();
        assertThat(destBatch.getQuantity()).isEqualByComparingTo(new BigDecimal("40"));

        // Transit: 0 (all consumed/written off)
        TransferInTransit transit = transferInTransitRepository
            .findByTransferId(transferId).get(0);
        assertThat(transit.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transit.getConsumedAt()).isNotNull();
    }
}
```

**Step 2: Run integration test**

Run: `./gradlew test --tests "br.com.stockshift.controller.TransferControllerIntegrationTest.FullTransferFlowWithDiscrepancy" --info`
Expected: PASS

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java
git commit -m "$(cat <<'EOF'
test(transfer): add full flow integration test with discrepancy

Covers complete transfer lifecycle: create -> dispatch -> validate
with shortage -> complete with discrepancy -> resolve as write-off.
Verifies ledger entries and final quantities at each stage.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Final Cleanup and Documentation

**Files:**
- Create: `docs/transfer-ledger-rules.md`

**Step 1: Create documentation**

```markdown
# Transfer Ledger Rules (Phase 4)

## Overview

The transfer system uses an append-only ledger to track all inventory movements. This ensures complete auditability and allows reconciliation of batch quantities.

## Ledger Entry Types for Transfers

| Type | When Created | Warehouse | Effect |
|------|--------------|-----------|--------|
| `TRANSFER_OUT` | Dispatch | Source | Debit (reduces stock) |
| `TRANSFER_IN_TRANSIT` | Dispatch | Virtual (null) | Credit (virtual holding) |
| `TRANSFER_IN` | Validation Complete | Destination | Credit (adds stock) |
| `TRANSFER_TRANSIT_CONSUMED` | Validation Complete | Virtual (null) | Debit (removes from transit) |
| `TRANSFER_LOSS` | Discrepancy Resolution | Virtual (null) | Debit (write-off shortage) |

## Discrepancy Handling

### Types
- **SHORTAGE**: Received less than expected
- **EXCESS**: Received more than expected

### Statuses
- **PENDING_RESOLUTION**: Awaiting action
- **RESOLVED**: Action taken (FOUND, ACCEPTED, RETURN_TRANSIT)
- **WRITTEN_OFF**: Written off as loss (WRITE_OFF)

### Resolution Options
- **WRITE_OFF**: Creates TRANSFER_LOSS ledger entry, zeros transit
- **FOUND**: Marks resolved, manual adjustment may follow
- **ACCEPTED**: For excess, marks resolved with audit flag
- **RETURN_TRANSIT**: Future: creates reverse transfer

## Reconciliation

Daily job at 2 AM compares:
- Batch materialized quantity (stored in `batches.quantity`)
- Calculated quantity (sum of ledger entries)

Discrepancies trigger critical alerts for investigation.

## API Endpoints

```
GET  /stockshift/transfers/{id}/discrepancies
POST /stockshift/transfers/discrepancies/{id}/resolve
GET  /stockshift/transfers/{id}/ledger
```
```

**Step 2: Commit documentation**

```bash
git add docs/transfer-ledger-rules.md
git commit -m "$(cat <<'EOF'
docs(transfer): add Phase 4 ledger rules documentation

Document ledger entry types, discrepancy handling, resolution
options, reconciliation job, and API endpoints.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

**Step 3: Final verification**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

Run: `./gradlew test`
Expected: All tests pass

---

## Summary

This plan implements Phase 4 (Ledger Rules) with:

1. **Discrepancy Enums** - DiscrepancyType, DiscrepancyStatus, DiscrepancyResolution
2. **NewTransferDiscrepancy Entity** - Links to Transfer/TransferItem with proper tracking
3. **DiscrepancyService** - Evaluates items, creates discrepancies, handles resolution
4. **TransferService Integration** - Creates discrepancies during validation completion
5. **Resolution Endpoints** - GET discrepancies, POST resolve with TRANSFER_LOSS
6. **Ledger Query Service** - Query ledger by transfer, batch, warehouse, product
7. **Reconciliation Job** - Daily verification of batch quantities vs ledger
8. **Full Integration Test** - End-to-end test covering discrepancy flow
9. **Documentation** - Complete reference for ledger rules

Total: 13 tasks, ~65 steps
