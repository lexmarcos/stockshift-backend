# Transfer Validation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement barcode-based validation for warehouse-to-warehouse transfers, with discrepancy reporting.

**Architecture:** New `TransferValidationService` handles validation lifecycle. Three new tables track validations, scanned items, and discrepancies. The existing `StockMovementService.executeMovement()` is modified to set status to `IN_TRANSIT` for transfers instead of `COMPLETED`. Stock enters destination warehouse only after validation completes.

**Tech Stack:** Spring Boot, JPA/Hibernate, PostgreSQL, Flyway migrations, JUnit 5 + Testcontainers, Apache POI (Excel export), OpenPDF (PDF export)

---

## Task 1: Add COMPLETED_WITH_DISCREPANCY to MovementStatus Enum

**Files:**
- Modify: `src/main/java/br/com/stockshift/model/enums/MovementStatus.java`

**Step 1: Add the new enum value**

```java
package br.com.stockshift.model.enums;

public enum MovementStatus {
    PENDING,    // Created but not executed
    IN_TRANSIT, // Product left origin, not arrived at destination
    COMPLETED,  // Movement completed
    COMPLETED_WITH_DISCREPANCY, // Movement completed but with missing items
    CANCELLED   // Movement cancelled
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/model/enums/MovementStatus.java
git commit -m "feat: add COMPLETED_WITH_DISCREPANCY status to MovementStatus enum"
```

---

## Task 2: Create ValidationStatus Enum

**Files:**
- Create: `src/main/java/br/com/stockshift/model/enums/ValidationStatus.java`

**Step 1: Create the enum**

```java
package br.com.stockshift.model.enums;

public enum ValidationStatus {
    IN_PROGRESS,  // Validation started, scanning in progress
    COMPLETED     // Validation finished
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/model/enums/ValidationStatus.java
git commit -m "feat: add ValidationStatus enum for transfer validations"
```

---

## Task 3: Create Database Migration for Transfer Validation Tables

**Files:**
- Create: `src/main/resources/db/migration/V16__create_transfer_validation_tables.sql`

**Step 1: Create the migration file**

```sql
-- Transfer validations table
CREATE TABLE transfer_validations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stock_movement_id UUID NOT NULL REFERENCES stock_movements(id) ON DELETE RESTRICT,
    validated_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    notes TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Transfer validation items table
CREATE TABLE transfer_validation_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_validation_id UUID NOT NULL REFERENCES transfer_validations(id) ON DELETE CASCADE,
    stock_movement_item_id UUID NOT NULL REFERENCES stock_movement_items(id) ON DELETE RESTRICT,
    expected_quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    scanned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Transfer discrepancies table
CREATE TABLE transfer_discrepancies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_validation_id UUID NOT NULL REFERENCES transfer_validations(id) ON DELETE CASCADE,
    stock_movement_item_id UUID NOT NULL REFERENCES stock_movement_items(id) ON DELETE RESTRICT,
    expected_quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL,
    missing_quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_transfer_validations_movement ON transfer_validations(stock_movement_id);
CREATE INDEX idx_transfer_validations_user ON transfer_validations(validated_by);
CREATE INDEX idx_transfer_validations_status ON transfer_validations(status);

CREATE INDEX idx_transfer_validation_items_validation ON transfer_validation_items(transfer_validation_id);
CREATE INDEX idx_transfer_validation_items_movement_item ON transfer_validation_items(stock_movement_item_id);

CREATE INDEX idx_transfer_discrepancies_validation ON transfer_discrepancies(transfer_validation_id);

-- Update triggers
CREATE TRIGGER update_transfer_validations_updated_at BEFORE UPDATE ON transfer_validations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transfer_validation_items_updated_at BEFORE UPDATE ON transfer_validation_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

**Step 2: Verify migration syntax by running tests**

Run: `./gradlew test --tests "StockshiftApplicationTests"`
Expected: BUILD SUCCESSFUL (Flyway will apply migration)

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V16__create_transfer_validation_tables.sql
git commit -m "feat: add database migration for transfer validation tables"
```

---

## Task 4: Create TransferValidation Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/TransferValidation.java`

**Step 1: Create the entity**

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.ValidationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transfer_validations")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class TransferValidation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_movement_id", nullable = false)
    private StockMovement stockMovement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by", nullable = false)
    private User validatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ValidationStatus status = ValidationStatus.IN_PROGRESS;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "transferValidation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransferValidationItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "transferValidation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransferDiscrepancy> discrepancies = new ArrayList<>();
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/TransferValidation.java
git commit -m "feat: add TransferValidation entity"
```

---

## Task 5: Create TransferValidationItem Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/TransferValidationItem.java`

**Step 1: Create the entity**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_validation_items")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class TransferValidationItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_validation_id", nullable = false)
    private TransferValidation transferValidation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_movement_item_id", nullable = false)
    private StockMovementItem stockMovementItem;

    @Column(name = "expected_quantity", nullable = false)
    private Integer expectedQuantity;

    @Column(name = "received_quantity", nullable = false)
    private Integer receivedQuantity = 0;

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/TransferValidationItem.java
git commit -m "feat: add TransferValidationItem entity"
```

---

## Task 6: Create TransferDiscrepancy Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/TransferDiscrepancy.java`

**Step 1: Create the entity**

```java
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
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/TransferDiscrepancy.java
git commit -m "feat: add TransferDiscrepancy entity"
```

---

## Task 7: Create Repositories

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/TransferValidationRepository.java`
- Create: `src/main/java/br/com/stockshift/repository/TransferValidationItemRepository.java`
- Create: `src/main/java/br/com/stockshift/repository/TransferDiscrepancyRepository.java`

**Step 1: Create TransferValidationRepository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferValidation;
import br.com.stockshift.model.enums.ValidationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferValidationRepository extends JpaRepository<TransferValidation, UUID> {

    Optional<TransferValidation> findByStockMovementIdAndStatus(UUID stockMovementId, ValidationStatus status);

    List<TransferValidation> findByStockMovementId(UUID stockMovementId);

    boolean existsByStockMovementIdAndStatusIn(UUID stockMovementId, List<ValidationStatus> statuses);
}
```

**Step 2: Create TransferValidationItemRepository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferValidationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferValidationItemRepository extends JpaRepository<TransferValidationItem, UUID> {

    List<TransferValidationItem> findByTransferValidationId(UUID transferValidationId);

    @Query("SELECT tvi FROM TransferValidationItem tvi " +
           "JOIN tvi.stockMovementItem smi " +
           "JOIN smi.product p " +
           "WHERE tvi.transferValidation.id = :validationId AND p.barcode = :barcode")
    Optional<TransferValidationItem> findByValidationIdAndProductBarcode(
            @Param("validationId") UUID validationId,
            @Param("barcode") String barcode);
}
```

**Step 3: Create TransferDiscrepancyRepository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferDiscrepancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferDiscrepancyRepository extends JpaRepository<TransferDiscrepancy, UUID> {

    List<TransferDiscrepancy> findByTransferValidationId(UUID transferValidationId);
}
```

**Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/TransferValidationRepository.java \
        src/main/java/br/com/stockshift/repository/TransferValidationItemRepository.java \
        src/main/java/br/com/stockshift/repository/TransferDiscrepancyRepository.java
git commit -m "feat: add repositories for transfer validation entities"
```

---

## Task 8: Create DTOs for Transfer Validation

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/validation/StartValidationResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/validation/ValidationItemResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/validation/ScanRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/validation/ScanResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/validation/ValidationProgressResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/validation/CompleteValidationResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/validation/DiscrepancyResponse.java`

**Step 1: Create ValidationItemResponse**

```java
package br.com.stockshift.dto.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationItemResponse {
    private UUID itemId;
    private UUID productId;
    private String productName;
    private String barcode;
    private Integer expectedQuantity;
    private Integer scannedQuantity;
    private String status; // PENDING, PARTIAL, COMPLETE
}
```

**Step 2: Create StartValidationResponse**

```java
package br.com.stockshift.dto.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartValidationResponse {
    private UUID validationId;
    private List<ValidationItemResponse> items;
    private LocalDateTime startedAt;
}
```

**Step 3: Create ScanRequest**

```java
package br.com.stockshift.dto.validation;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanRequest {
    @NotBlank(message = "Barcode is required")
    private String barcode;
}
```

**Step 4: Create ScanResponse**

```java
package br.com.stockshift.dto.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanResponse {
    private boolean success;
    private String message;
    private String barcode;
    private ValidationItemResponse item;
}
```

**Step 5: Create ValidationProgressResponse**

```java
package br.com.stockshift.dto.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationProgressResponse {
    private UUID validationId;
    private String status;
    private LocalDateTime startedAt;
    private List<ValidationItemResponse> items;
    private ProgressSummary progress;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgressSummary {
        private int totalItems;
        private int completeItems;
        private int partialItems;
        private int pendingItems;
    }
}
```

**Step 6: Create DiscrepancyResponse**

```java
package br.com.stockshift.dto.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyResponse {
    private UUID productId;
    private String productName;
    private Integer expected;
    private Integer received;
    private Integer missing;
}
```

**Step 7: Create CompleteValidationResponse**

```java
package br.com.stockshift.dto.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteValidationResponse {
    private UUID validationId;
    private String status;
    private LocalDateTime completedAt;
    private ValidationSummary summary;
    private List<DiscrepancyResponse> discrepancies;
    private String reportUrl;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationSummary {
        private int totalExpected;
        private int totalReceived;
        private int totalMissing;
    }
}
```

**Step 8: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 9: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/validation/
git commit -m "feat: add DTOs for transfer validation endpoints"
```

---

## Task 9: Create TransferValidationService

**Files:**
- Create: `src/main/java/br/com/stockshift/service/TransferValidationService.java`

**Step 1: Create the service**

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.validation.*;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.model.enums.MovementType;
import br.com.stockshift.model.enums.ValidationStatus;
import br.com.stockshift.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferValidationService {

    private final TransferValidationRepository validationRepository;
    private final TransferValidationItemRepository validationItemRepository;
    private final TransferDiscrepancyRepository discrepancyRepository;
    private final StockMovementRepository movementRepository;
    private final BatchRepository batchRepository;
    private final UserRepository userRepository;

    @Transactional
    public StartValidationResponse startValidation(UUID movementId) {
        StockMovement movement = movementRepository.findById(movementId)
                .orElseThrow(() -> new ResourceNotFoundException("StockMovement", "id", movementId));

        // Validate movement type and status
        if (movement.getMovementType() != MovementType.TRANSFER) {
            throw new BusinessException("Only TRANSFER movements can be validated");
        }

        if (movement.getStatus() != MovementStatus.IN_TRANSIT) {
            throw new BusinessException("Only IN_TRANSIT movements can be validated");
        }

        // Check if validation already exists
        List<ValidationStatus> blockingStatuses = List.of(ValidationStatus.IN_PROGRESS, ValidationStatus.COMPLETED);
        if (validationRepository.existsByStockMovementIdAndStatusIn(movementId, blockingStatuses)) {
            throw new BusinessException("A validation already exists for this movement");
        }

        // Get current user
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        // TODO: Validate user belongs to destination warehouse (future enhancement)

        // Create validation
        TransferValidation validation = new TransferValidation();
        validation.setStockMovement(movement);
        validation.setValidatedBy(user);
        validation.setStatus(ValidationStatus.IN_PROGRESS);
        validation.setStartedAt(LocalDateTime.now());

        // Create validation items from movement items
        List<TransferValidationItem> validationItems = new ArrayList<>();
        for (StockMovementItem movementItem : movement.getItems()) {
            TransferValidationItem validationItem = new TransferValidationItem();
            validationItem.setTransferValidation(validation);
            validationItem.setStockMovementItem(movementItem);
            validationItem.setExpectedQuantity(movementItem.getQuantity());
            validationItem.setReceivedQuantity(0);
            validationItems.add(validationItem);
        }
        validation.setItems(validationItems);

        TransferValidation saved = validationRepository.save(validation);
        log.info("Started validation {} for movement {}", saved.getId(), movementId);

        return StartValidationResponse.builder()
                .validationId(saved.getId())
                .startedAt(saved.getStartedAt())
                .items(mapItemsToResponse(saved.getItems()))
                .build();
    }

    @Transactional
    public ScanResponse scanBarcode(UUID movementId, UUID validationId, String barcode) {
        TransferValidation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("TransferValidation", "id", validationId));

        // Verify validation belongs to movement
        if (!validation.getStockMovement().getId().equals(movementId)) {
            throw new BusinessException("Validation does not belong to this movement");
        }

        if (validation.getStatus() != ValidationStatus.IN_PROGRESS) {
            throw new BusinessException("Cannot scan items on a completed validation");
        }

        // Find item by barcode
        TransferValidationItem item = validationItemRepository
                .findByValidationIdAndProductBarcode(validationId, barcode)
                .orElse(null);

        if (item == null) {
            log.warn("Scan attempt with unknown barcode {} for validation {}", barcode, validationId);
            return ScanResponse.builder()
                    .success(false)
                    .message("Produto não faz parte desta transferência")
                    .barcode(barcode)
                    .build();
        }

        // Increment received quantity
        item.setReceivedQuantity(item.getReceivedQuantity() + 1);
        item.setScannedAt(LocalDateTime.now());
        validationItemRepository.save(item);

        log.info("Scanned barcode {} for validation {}, new quantity: {}/{}",
                barcode, validationId, item.getReceivedQuantity(), item.getExpectedQuantity());

        return ScanResponse.builder()
                .success(true)
                .message("Produto escaneado com sucesso")
                .barcode(barcode)
                .item(mapItemToResponse(item))
                .build();
    }

    @Transactional(readOnly = true)
    public ValidationProgressResponse getProgress(UUID movementId, UUID validationId) {
        TransferValidation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("TransferValidation", "id", validationId));

        if (!validation.getStockMovement().getId().equals(movementId)) {
            throw new BusinessException("Validation does not belong to this movement");
        }

        List<ValidationItemResponse> items = mapItemsToResponse(validation.getItems());

        int complete = 0, partial = 0, pending = 0;
        for (ValidationItemResponse item : items) {
            switch (item.getStatus()) {
                case "COMPLETE" -> complete++;
                case "PARTIAL" -> partial++;
                case "PENDING" -> pending++;
            }
        }

        return ValidationProgressResponse.builder()
                .validationId(validation.getId())
                .status(validation.getStatus().name())
                .startedAt(validation.getStartedAt())
                .items(items)
                .progress(ValidationProgressResponse.ProgressSummary.builder()
                        .totalItems(items.size())
                        .completeItems(complete)
                        .partialItems(partial)
                        .pendingItems(pending)
                        .build())
                .build();
    }

    @Transactional
    public CompleteValidationResponse completeValidation(UUID movementId, UUID validationId) {
        TransferValidation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("TransferValidation", "id", validationId));

        if (!validation.getStockMovement().getId().equals(movementId)) {
            throw new BusinessException("Validation does not belong to this movement");
        }

        if (validation.getStatus() != ValidationStatus.IN_PROGRESS) {
            throw new BusinessException("Validation is already completed");
        }

        StockMovement movement = validation.getStockMovement();
        List<TransferDiscrepancy> discrepancies = new ArrayList<>();
        int totalExpected = 0;
        int totalReceived = 0;

        // Process each item
        for (TransferValidationItem item : validation.getItems()) {
            totalExpected += item.getExpectedQuantity();
            totalReceived += item.getReceivedQuantity();

            // Add received quantity to destination warehouse
            if (item.getReceivedQuantity() > 0) {
                addStockToDestination(movement, item.getStockMovementItem(), item.getReceivedQuantity());
            }

            // Create discrepancy record if missing items
            if (item.getReceivedQuantity() < item.getExpectedQuantity()) {
                TransferDiscrepancy discrepancy = new TransferDiscrepancy();
                discrepancy.setTransferValidation(validation);
                discrepancy.setStockMovementItem(item.getStockMovementItem());
                discrepancy.setExpectedQuantity(item.getExpectedQuantity());
                discrepancy.setReceivedQuantity(item.getReceivedQuantity());
                discrepancy.setMissingQuantity(item.getExpectedQuantity() - item.getReceivedQuantity());
                discrepancies.add(discrepancy);
            }
        }

        // Save discrepancies
        if (!discrepancies.isEmpty()) {
            discrepancyRepository.saveAll(discrepancies);
        }

        // Update validation status
        validation.setStatus(ValidationStatus.COMPLETED);
        validation.setCompletedAt(LocalDateTime.now());
        validationRepository.save(validation);

        // Update movement status
        MovementStatus newStatus = discrepancies.isEmpty()
                ? MovementStatus.COMPLETED
                : MovementStatus.COMPLETED_WITH_DISCREPANCY;
        movement.setStatus(newStatus);
        movement.setCompletedAt(LocalDateTime.now());
        movementRepository.save(movement);

        log.info("Completed validation {} for movement {}. Status: {}, Discrepancies: {}",
                validationId, movementId, newStatus, discrepancies.size());

        List<DiscrepancyResponse> discrepancyResponses = discrepancies.stream()
                .map(d -> DiscrepancyResponse.builder()
                        .productId(d.getStockMovementItem().getProduct().getId())
                        .productName(d.getStockMovementItem().getProduct().getName())
                        .expected(d.getExpectedQuantity())
                        .received(d.getReceivedQuantity())
                        .missing(d.getMissingQuantity())
                        .build())
                .collect(Collectors.toList());

        String reportUrl = discrepancies.isEmpty() ? null
                : String.format("/api/stock-movements/%s/validations/%s/discrepancy-report", movementId, validationId);

        return CompleteValidationResponse.builder()
                .validationId(validation.getId())
                .status(newStatus.name())
                .completedAt(validation.getCompletedAt())
                .summary(CompleteValidationResponse.ValidationSummary.builder()
                        .totalExpected(totalExpected)
                        .totalReceived(totalReceived)
                        .totalMissing(totalExpected - totalReceived)
                        .build())
                .discrepancies(discrepancyResponses)
                .reportUrl(reportUrl)
                .build();
    }

    private void addStockToDestination(StockMovement movement, StockMovementItem item, int quantity) {
        Batch sourceBatch = item.getBatch();
        Warehouse destWarehouse = movement.getDestinationWarehouse();

        // Find or create batch in destination warehouse
        List<Batch> destBatches = batchRepository.findByProductIdAndWarehouseIdAndTenantId(
                item.getProduct().getId(),
                destWarehouse.getId(),
                movement.getTenantId());

        Batch destBatch;
        if (!destBatches.isEmpty()) {
            destBatch = destBatches.get(0);
            destBatch.setQuantity(destBatch.getQuantity() + quantity);
        } else {
            destBatch = new Batch();
            destBatch.setTenantId(movement.getTenantId());
            destBatch.setProduct(item.getProduct());
            destBatch.setWarehouse(destWarehouse);
            destBatch.setBatchCode(sourceBatch.getBatchCode() + "-TRANSFER");
            destBatch.setQuantity(quantity);
            destBatch.setManufacturedDate(sourceBatch.getManufacturedDate());
            destBatch.setExpirationDate(sourceBatch.getExpirationDate());
            destBatch.setCostPrice(sourceBatch.getCostPrice());
            destBatch.setSellingPrice(sourceBatch.getSellingPrice());
        }
        batchRepository.save(destBatch);
    }

    private List<ValidationItemResponse> mapItemsToResponse(List<TransferValidationItem> items) {
        return items.stream()
                .map(this::mapItemToResponse)
                .collect(Collectors.toList());
    }

    private ValidationItemResponse mapItemToResponse(TransferValidationItem item) {
        Product product = item.getStockMovementItem().getProduct();
        String status;
        if (item.getReceivedQuantity() == 0) {
            status = "PENDING";
        } else if (item.getReceivedQuantity() >= item.getExpectedQuantity()) {
            status = "COMPLETE";
        } else {
            status = "PARTIAL";
        }

        return ValidationItemResponse.builder()
                .itemId(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .barcode(product.getBarcode())
                .expectedQuantity(item.getExpectedQuantity())
                .scannedQuantity(item.getReceivedQuantity())
                .status(status)
                .build();
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/service/TransferValidationService.java
git commit -m "feat: add TransferValidationService with core validation logic"
```

---

## Task 10: Modify StockMovementService for IN_TRANSIT Status

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/StockMovementService.java`

**Step 1: Modify executeMovement to handle TRANSFER differently**

Find the `applyStockChanges` method TRANSFER case (around line 247-278) and modify it to only decrease source stock, not add to destination:

Replace the entire TRANSFER case:

```java
case TRANSFER:
    // Only decrease from source - destination will be handled by validation
    if (batch.getQuantity() < item.getQuantity()) {
        throw new BusinessException("Insufficient stock for product " + item.getProduct().getName());
    }
    batch.setQuantity(batch.getQuantity() - item.getQuantity());
    batchRepository.save(batch);
    // Note: Stock will be added to destination warehouse during validation
    break;
```

**Step 2: Modify executeMovement to set IN_TRANSIT for transfers**

Find the status update line (around line 138) and modify it:

Replace:
```java
movement.setStatus(MovementStatus.COMPLETED);
movement.setCompletedAt(LocalDateTime.now());
```

With:
```java
if (movement.getMovementType() == MovementType.TRANSFER) {
    movement.setStatus(MovementStatus.IN_TRANSIT);
    // completedAt will be set when validation is completed
} else {
    movement.setStatus(MovementStatus.COMPLETED);
    movement.setCompletedAt(LocalDateTime.now());
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/service/StockMovementService.java
git commit -m "feat: modify executeMovement to set IN_TRANSIT for transfers"
```

---

## Task 11: Create TransferValidationController

**Files:**
- Create: `src/main/java/br/com/stockshift/controller/TransferValidationController.java`

**Step 1: Create the controller**

```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.validation.*;
import br.com.stockshift.service.TransferValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/stock-movements/{movementId}/validations")
@RequiredArgsConstructor
@Tag(name = "Transfer Validations", description = "Transfer validation endpoints for barcode scanning")
@SecurityRequirement(name = "Bearer Authentication")
public class TransferValidationController {

    private final TransferValidationService validationService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_EXECUTE', 'ROLE_ADMIN')")
    @Operation(summary = "Start a new validation for a transfer")
    public ResponseEntity<ApiResponse<StartValidationResponse>> startValidation(
            @PathVariable UUID movementId) {
        StartValidationResponse response = validationService.startValidation(movementId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Validation started successfully", response));
    }

    @PostMapping("/{validationId}/scan")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_EXECUTE', 'ROLE_ADMIN')")
    @Operation(summary = "Scan a barcode during validation")
    public ResponseEntity<ApiResponse<ScanResponse>> scanBarcode(
            @PathVariable UUID movementId,
            @PathVariable UUID validationId,
            @Valid @RequestBody ScanRequest request) {
        ScanResponse response = validationService.scanBarcode(movementId, validationId, request.getBarcode());
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @GetMapping("/{validationId}")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get validation progress")
    public ResponseEntity<ApiResponse<ValidationProgressResponse>> getProgress(
            @PathVariable UUID movementId,
            @PathVariable UUID validationId) {
        ValidationProgressResponse response = validationService.getProgress(movementId, validationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{validationId}/complete")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_EXECUTE', 'ROLE_ADMIN')")
    @Operation(summary = "Complete a validation")
    public ResponseEntity<ApiResponse<CompleteValidationResponse>> completeValidation(
            @PathVariable UUID movementId,
            @PathVariable UUID validationId) {
        CompleteValidationResponse response = validationService.completeValidation(movementId, validationId);
        return ResponseEntity.ok(ApiResponse.success("Validation completed successfully", response));
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/TransferValidationController.java
git commit -m "feat: add TransferValidationController with validation endpoints"
```

---

## Task 12: Add Report Generation Dependencies

**Files:**
- Modify: `build.gradle`

**Step 1: Add Apache POI and OpenPDF dependencies**

Add to the dependencies section:

```groovy
// Report generation
implementation 'org.apache.poi:poi-ooxml:5.2.5'
implementation 'com.github.librepdf:openpdf:1.3.35'
```

**Step 2: Verify dependencies resolve**

Run: `./gradlew dependencies --configuration compileClasspath | grep -E "(poi|openpdf)"`
Expected: Should show the new dependencies

**Step 3: Commit**

```bash
git add build.gradle
git commit -m "feat: add Apache POI and OpenPDF dependencies for report generation"
```

---

## Task 13: Create DiscrepancyReportService

**Files:**
- Create: `src/main/java/br/com/stockshift/service/DiscrepancyReportService.java`

**Step 1: Create the service**

```java
package br.com.stockshift.service;

import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.entity.TransferDiscrepancy;
import br.com.stockshift.model.entity.TransferValidation;
import br.com.stockshift.model.enums.ValidationStatus;
import br.com.stockshift.repository.TransferDiscrepancyRepository;
import br.com.stockshift.repository.TransferValidationRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscrepancyReportService {

    private final TransferValidationRepository validationRepository;
    private final TransferDiscrepancyRepository discrepancyRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transactional(readOnly = true)
    public byte[] generatePdfReport(UUID movementId, UUID validationId) {
        TransferValidation validation = getValidation(movementId, validationId);
        List<TransferDiscrepancy> discrepancies = discrepancyRepository.findByTransferValidationId(validationId);

        if (discrepancies.isEmpty()) {
            throw new BusinessException("No discrepancies found for this validation");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Relatório de Discrepâncias", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Movement info
            StockMovement movement = validation.getStockMovement();
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 11);

            document.add(new Paragraph("Transferência: " + movement.getId(), normalFont));
            document.add(new Paragraph("Origem: " + movement.getSourceWarehouse().getName(), normalFont));
            document.add(new Paragraph("Destino: " + movement.getDestinationWarehouse().getName(), normalFont));
            document.add(new Paragraph("Data da Validação: " + validation.getCompletedAt().format(DATE_FORMATTER), normalFont));
            document.add(new Paragraph("Validado por: " + validation.getValidatedBy().getFullName(), normalFont));
            document.add(new Paragraph(" "));

            // Table
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3, 1, 1, 1});

            // Header
            addTableHeader(table, "Produto", headerFont);
            addTableHeader(table, "Esperado", headerFont);
            addTableHeader(table, "Recebido", headerFont);
            addTableHeader(table, "Faltante", headerFont);

            // Data rows
            int totalExpected = 0, totalReceived = 0, totalMissing = 0;
            for (TransferDiscrepancy d : discrepancies) {
                table.addCell(new PdfPCell(new Phrase(d.getStockMovementItem().getProduct().getName(), normalFont)));
                table.addCell(new PdfPCell(new Phrase(String.valueOf(d.getExpectedQuantity()), normalFont)));
                table.addCell(new PdfPCell(new Phrase(String.valueOf(d.getReceivedQuantity()), normalFont)));
                table.addCell(new PdfPCell(new Phrase(String.valueOf(d.getMissingQuantity()), normalFont)));

                totalExpected += d.getExpectedQuantity();
                totalReceived += d.getReceivedQuantity();
                totalMissing += d.getMissingQuantity();
            }

            // Totals
            addTableHeader(table, "TOTAL", headerFont);
            addTableHeader(table, String.valueOf(totalExpected), headerFont);
            addTableHeader(table, String.valueOf(totalReceived), headerFont);
            addTableHeader(table, String.valueOf(totalMissing), headerFont);

            document.add(table);
            document.close();

            log.info("Generated PDF discrepancy report for validation {}", validationId);
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new BusinessException("Error generating PDF report: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateExcelReport(UUID movementId, UUID validationId) {
        TransferValidation validation = getValidation(movementId, validationId);
        List<TransferDiscrepancy> discrepancies = discrepancyRepository.findByTransferValidationId(validationId);

        if (discrepancies.isEmpty()) {
            throw new BusinessException("No discrepancies found for this validation");
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Discrepâncias");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Movement info
            StockMovement movement = validation.getStockMovement();
            int rowNum = 0;

            Row infoRow1 = sheet.createRow(rowNum++);
            infoRow1.createCell(0).setCellValue("Transferência:");
            infoRow1.createCell(1).setCellValue(movement.getId().toString());

            Row infoRow2 = sheet.createRow(rowNum++);
            infoRow2.createCell(0).setCellValue("Origem:");
            infoRow2.createCell(1).setCellValue(movement.getSourceWarehouse().getName());

            Row infoRow3 = sheet.createRow(rowNum++);
            infoRow3.createCell(0).setCellValue("Destino:");
            infoRow3.createCell(1).setCellValue(movement.getDestinationWarehouse().getName());

            Row infoRow4 = sheet.createRow(rowNum++);
            infoRow4.createCell(0).setCellValue("Data da Validação:");
            infoRow4.createCell(1).setCellValue(validation.getCompletedAt().format(DATE_FORMATTER));

            Row infoRow5 = sheet.createRow(rowNum++);
            infoRow5.createCell(0).setCellValue("Validado por:");
            infoRow5.createCell(1).setCellValue(validation.getValidatedBy().getFullName());

            rowNum++; // Empty row

            // Header row
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Produto", "Código de Barras", "Esperado", "Recebido", "Faltante"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int totalExpected = 0, totalReceived = 0, totalMissing = 0;
            for (TransferDiscrepancy d : discrepancies) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(d.getStockMovementItem().getProduct().getName());
                row.createCell(1).setCellValue(d.getStockMovementItem().getProduct().getBarcode());
                row.createCell(2).setCellValue(d.getExpectedQuantity());
                row.createCell(3).setCellValue(d.getReceivedQuantity());
                row.createCell(4).setCellValue(d.getMissingQuantity());

                totalExpected += d.getExpectedQuantity();
                totalReceived += d.getReceivedQuantity();
                totalMissing += d.getMissingQuantity();
            }

            // Totals row
            Row totalRow = sheet.createRow(rowNum);
            Cell totalLabelCell = totalRow.createCell(0);
            totalLabelCell.setCellValue("TOTAL");
            totalLabelCell.setCellStyle(headerStyle);
            totalRow.createCell(2).setCellValue(totalExpected);
            totalRow.createCell(3).setCellValue(totalReceived);
            totalRow.createCell(4).setCellValue(totalMissing);

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            log.info("Generated Excel discrepancy report for validation {}", validationId);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Error generating Excel report: " + e.getMessage());
        }
    }

    private TransferValidation getValidation(UUID movementId, UUID validationId) {
        TransferValidation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("TransferValidation", "id", validationId));

        if (!validation.getStockMovement().getId().equals(movementId)) {
            throw new BusinessException("Validation does not belong to this movement");
        }

        if (validation.getStatus() != ValidationStatus.COMPLETED) {
            throw new BusinessException("Report can only be generated for completed validations");
        }

        return validation;
    }

    private void addTableHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
        cell.setPadding(5);
        table.addCell(cell);
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/service/DiscrepancyReportService.java
git commit -m "feat: add DiscrepancyReportService for PDF and Excel report generation"
```

---

## Task 14: Add Report Endpoint to Controller

**Files:**
- Modify: `src/main/java/br/com/stockshift/controller/TransferValidationController.java`

**Step 1: Add the report endpoint**

Add the following imports and method to `TransferValidationController`:

```java
// Add these imports at the top
import br.com.stockshift.service.DiscrepancyReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

// Add this field
private final DiscrepancyReportService reportService;

// Add this endpoint method
@GetMapping("/{validationId}/discrepancy-report")
@PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_READ', 'ROLE_ADMIN')")
@Operation(summary = "Download discrepancy report")
public ResponseEntity<byte[]> getDiscrepancyReport(
        @PathVariable UUID movementId,
        @PathVariable UUID validationId,
        @RequestHeader(value = "Accept", defaultValue = "application/pdf") String acceptHeader) {

    byte[] report;
    String contentType;
    String filename;

    if (acceptHeader.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
        report = reportService.generateExcelReport(movementId, validationId);
        contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        filename = "discrepancy-report-" + validationId + ".xlsx";
    } else {
        report = reportService.generatePdfReport(movementId, validationId);
        contentType = "application/pdf";
        filename = "discrepancy-report-" + validationId + ".pdf";
    }

    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType(contentType))
            .body(report);
}
```

**Step 2: Update constructor to include DiscrepancyReportService**

The `@RequiredArgsConstructor` will auto-generate the constructor, just add the field.

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/TransferValidationController.java
git commit -m "feat: add discrepancy report download endpoint"
```

---

## Task 15: Create Integration Tests for Transfer Validation

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/TransferValidationControllerIntegrationTest.java`

**Step 1: Create the test class**

```java
package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.movement.StockMovementItemRequest;
import br.com.stockshift.dto.movement.StockMovementRequest;
import br.com.stockshift.dto.validation.ScanRequest;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.MovementType;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class TransferValidationControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransferValidationRepository transferValidationRepository;

    private Tenant testTenant;
    private User testUser;
    private Category testCategory;
    private Product testProduct;
    private Warehouse sourceWarehouse;
    private Warehouse destWarehouse;
    private Batch testBatch;

    @BeforeEach
    void setUpTestData() {
        transferValidationRepository.deleteAll();
        stockMovementRepository.deleteAll();
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Validation Test Tenant", "66666666000106");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "validation@test.com");

        TenantContext.setTenantId(testTenant.getId());

        testCategory = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Validation Category");
        testProduct = TestDataFactory.createProduct(productRepository, testTenant.getId(),
                testCategory, "Validation Product", "SKU-VAL-001");
        testProduct.setBarcode("7891234567890");
        productRepository.save(testProduct);

        sourceWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Source Warehouse");
        destWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Destination Warehouse");
        testBatch = TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                testProduct, sourceWarehouse, 100);
    }

    @Test
    @WithMockUser(username = "validation@test.com", authorities = {"ROLE_ADMIN"})
    void shouldStartValidationForInTransitTransfer() throws Exception {
        // Create and execute transfer
        String movementId = createAndExecuteTransfer();

        // Start validation
        mockMvc.perform(post("/api/stock-movements/{id}/validations", movementId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.validationId").exists())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].expectedQuantity").value(10));
    }

    @Test
    @WithMockUser(username = "validation@test.com", authorities = {"ROLE_ADMIN"})
    void shouldScanBarcodeSuccessfully() throws Exception {
        String movementId = createAndExecuteTransfer();

        // Start validation
        String validationResponse = mockMvc.perform(post("/api/stock-movements/{id}/validations", movementId))
                .andReturn().getResponse().getContentAsString();
        String validationId = objectMapper.readTree(validationResponse).get("data").get("validationId").asText();

        // Scan barcode
        ScanRequest scanRequest = ScanRequest.builder().barcode("7891234567890").build();

        mockMvc.perform(post("/api/stock-movements/{movementId}/validations/{validationId}/scan",
                        movementId, validationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scanRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.item.scannedQuantity").value(1));
    }

    @Test
    @WithMockUser(username = "validation@test.com", authorities = {"ROLE_ADMIN"})
    void shouldRejectUnknownBarcode() throws Exception {
        String movementId = createAndExecuteTransfer();

        String validationResponse = mockMvc.perform(post("/api/stock-movements/{id}/validations", movementId))
                .andReturn().getResponse().getContentAsString();
        String validationId = objectMapper.readTree(validationResponse).get("data").get("validationId").asText();

        ScanRequest scanRequest = ScanRequest.builder().barcode("UNKNOWN123").build();

        mockMvc.perform(post("/api/stock-movements/{movementId}/validations/{validationId}/scan",
                        movementId, validationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scanRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.message").value("Produto não faz parte desta transferência"));
    }

    @Test
    @WithMockUser(username = "validation@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCompleteValidationWithDiscrepancy() throws Exception {
        String movementId = createAndExecuteTransfer();

        // Start validation
        String validationResponse = mockMvc.perform(post("/api/stock-movements/{id}/validations", movementId))
                .andReturn().getResponse().getContentAsString();
        String validationId = objectMapper.readTree(validationResponse).get("data").get("validationId").asText();

        // Scan only 5 of 10 expected
        ScanRequest scanRequest = ScanRequest.builder().barcode("7891234567890").build();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/stock-movements/{movementId}/validations/{validationId}/scan",
                            movementId, validationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(scanRequest)));
        }

        // Complete validation
        mockMvc.perform(post("/api/stock-movements/{movementId}/validations/{validationId}/complete",
                        movementId, validationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED_WITH_DISCREPANCY"))
                .andExpect(jsonPath("$.data.summary.totalExpected").value(10))
                .andExpect(jsonPath("$.data.summary.totalReceived").value(5))
                .andExpect(jsonPath("$.data.summary.totalMissing").value(5))
                .andExpect(jsonPath("$.data.discrepancies[0].missing").value(5));
    }

    @Test
    @WithMockUser(username = "validation@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCompleteValidationWithoutDiscrepancy() throws Exception {
        String movementId = createAndExecuteTransfer();

        String validationResponse = mockMvc.perform(post("/api/stock-movements/{id}/validations", movementId))
                .andReturn().getResponse().getContentAsString();
        String validationId = objectMapper.readTree(validationResponse).get("data").get("validationId").asText();

        // Scan all 10 expected
        ScanRequest scanRequest = ScanRequest.builder().barcode("7891234567890").build();
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/stock-movements/{movementId}/validations/{validationId}/scan",
                            movementId, validationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(scanRequest)));
        }

        mockMvc.perform(post("/api/stock-movements/{movementId}/validations/{validationId}/complete",
                        movementId, validationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.discrepancies").isEmpty());
    }

    private String createAndExecuteTransfer() throws Exception {
        StockMovementItemRequest itemRequest = StockMovementItemRequest.builder()
                .productId(testProduct.getId())
                .batchId(testBatch.getId())
                .quantity(10)
                .build();

        StockMovementRequest request = StockMovementRequest.builder()
                .movementType(MovementType.TRANSFER)
                .sourceWarehouseId(sourceWarehouse.getId())
                .destinationWarehouseId(destWarehouse.getId())
                .items(Collections.singletonList(itemRequest))
                .notes("Test transfer")
                .build();

        String createResponse = mockMvc.perform(post("/api/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String movementId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        // Execute to set IN_TRANSIT
        mockMvc.perform(post("/api/stock-movements/{id}/execute", movementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));

        return movementId;
    }
}
```

**Step 2: Run the tests**

Run: `./gradlew test --tests "TransferValidationControllerIntegrationTest"`
Expected: All tests should pass

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/TransferValidationControllerIntegrationTest.java
git commit -m "test: add integration tests for transfer validation endpoints"
```

---

## Task 16: Run All Tests and Final Verification

**Step 1: Run all tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 2: Verify build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 3: Final commit with summary**

```bash
git add -A
git commit -m "feat: complete transfer validation feature implementation

Implemented barcode-based validation for warehouse transfers:
- New IN_TRANSIT flow for transfers
- Barcode scanning with real-time feedback
- Partial validation with automatic discrepancy tracking
- PDF and Excel discrepancy report export
- Full integration test coverage"
```

---

## Summary

This plan implements the transfer validation feature with:

1. **Database changes**: 3 new tables + 1 enum value
2. **Entities**: TransferValidation, TransferValidationItem, TransferDiscrepancy
3. **Service layer**: TransferValidationService, DiscrepancyReportService
4. **API endpoints**: 5 new endpoints under `/api/stock-movements/{id}/validations`
5. **Reports**: PDF and Excel export for discrepancies
6. **Tests**: Integration tests covering happy path and edge cases

**Files created/modified**: ~20 files
**Estimated time**: 2-3 hours with TDD approach
