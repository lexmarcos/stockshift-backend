# Transfer Refactoring Phase 3 - API Redesign & Ledger Integration

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the new Transfer API endpoints (`/stockshift/transfers`) with full ledger integration, replacing the legacy stock-movement TRANSFER type with a dedicated, role-aware workflow.

**Architecture:** Create a complete DTO layer for Transfer operations, integrate append-only InventoryLedger for dispatch/completion, implement mirror-batch creation at destination, and expose all endpoints via `TransferController`. The service layer orchestrates state transitions, ledger entries, and batch updates within atomic transactions.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Spring Security, Hibernate, JUnit 5, Mockito, Testcontainers, AssertJ

**Reference Spec:** `.claude/refactoring/transfer-refactoring-spec.md` (Etapa 3 & Etapa 4)

---

## Overview

Phase 3 implements the business logic and public API:

1. **DTOs:** Request/Response objects for the Transfer API
2. **Transfer Creation:** `createTransfer` with validation and code generation
3. **Transfer Update:** `updateTransfer` for DRAFT modifications
4. **Ledger Integration (Dispatch):** Record TRANSFER_OUT, TRANSFER_IN_TRANSIT, create TransferInTransit
5. **Scan Logic:** `scanItem` with idempotency support
6. **Ledger Integration (Completion):** Record TRANSFER_IN, TRANSFER_TRANSIT_CONSUMED, mirror batches
7. **TransferController:** All endpoints per spec
8. **Integration Tests:** E2E validation of full flow

---

## Task 1: Create Transfer Request DTOs

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/transfer/CreateTransferRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/TransferItemRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/UpdateTransferRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/ScanItemRequest.java`

**Step 1: Create CreateTransferRequest**

```java
package br.com.stockshift.dto.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransferRequest {

    @NotNull(message = "Source warehouse ID is required")
    private UUID sourceWarehouseId;

    @NotNull(message = "Destination warehouse ID is required")
    private UUID destinationWarehouseId;

    @NotEmpty(message = "Transfer must have at least one item")
    @Valid
    private List<TransferItemRequest> items;

    private String notes;
}
```

**Step 2: Create TransferItemRequest**

```java
package br.com.stockshift.dto.transfer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferItemRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Batch ID is required")
    private UUID batchId;

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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTransferRequest {

    @Valid
    private List<TransferItemRequest> items;

    private String notes;
}
```

**Step 4: Create ScanItemRequest**

```java
package br.com.stockshift.dto.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanItemRequest {

    @NotBlank(message = "Barcode is required")
    private String barcode;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    private UUID idempotencyKey;
}
```

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/transfer/
git commit -m "$(cat <<'EOF'
feat(transfer): add request DTOs for Transfer API

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Create Transfer Response DTOs

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/transfer/TransferResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/TransferItemResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/TransferSummaryResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/TransferValidationResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/WarehouseSummary.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/UserSummary.java`

**Step 1: Create WarehouseSummary**

```java
package br.com.stockshift.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseSummary {
    private UUID id;
    private String name;
    private String code;
}
```

**Step 2: Create UserSummary**

```java
package br.com.stockshift.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {
    private UUID id;
    private String name;
    private String email;
}
```

**Step 3: Create TransferItemResponse**

```java
package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferItemStatus;
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
    private UUID productId;
    private String productName;
    private String productSku;
    private UUID sourceBatchId;
    private String sourceBatchCode;
    private UUID destinationBatchId;
    private String destinationBatchCode;
    private BigDecimal expectedQuantity;
    private BigDecimal receivedQuantity;
    private TransferItemStatus status;
}
```

**Step 4: Create TransferSummaryResponse (for listings)**

```java
package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.model.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferSummaryResponse {
    private UUID id;
    private String transferCode;
    private TransferStatus status;
    private WarehouseSummary sourceWarehouse;
    private WarehouseSummary destinationWarehouse;
    private TransferRole direction;
    private List<TransferAction> allowedActions;
    private int itemCount;
    private BigDecimal totalQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime completedAt;
}
```

**Step 5: Create TransferResponse (detailed)**

```java
package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.model.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {
    private UUID id;
    private String transferCode;
    private TransferStatus status;

    private WarehouseSummary sourceWarehouse;
    private WarehouseSummary destinationWarehouse;

    private List<TransferItemResponse> items;

    private TransferRole direction;
    private List<TransferAction> allowedActions;

    // Summary
    private int totalItems;
    private BigDecimal totalExpectedQuantity;
    private BigDecimal totalReceivedQuantity;
    private int itemsValidated;
    private boolean hasDiscrepancy;

    // Audit
    private UserSummary createdBy;
    private LocalDateTime createdAt;
    private UserSummary dispatchedBy;
    private LocalDateTime dispatchedAt;
    private UserSummary validationStartedBy;
    private LocalDateTime validationStartedAt;
    private UserSummary completedBy;
    private LocalDateTime completedAt;
    private UserSummary cancelledBy;
    private LocalDateTime cancelledAt;
    private String cancellationReason;

    private String notes;
}
```

**Step 6: Create TransferValidationResponse**

```java
package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferStatus;
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
public class TransferValidationResponse {
    private UUID transferId;
    private TransferStatus status;
    private LocalDateTime validationStartedAt;
    private UserSummary validationStartedBy;
    private List<TransferItemResponse> items;

    // Validation summary
    private int totalItems;
    private int itemsScanned;
    private int itemsPending;
    private boolean hasDiscrepancy;
    private boolean canComplete;

    private List<TransferAction> allowedActions;
}
```

**Step 7: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/transfer/
git commit -m "$(cat <<'EOF'
feat(transfer): add response DTOs for Transfer API

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Create TransferMapper

**Files:**
- Create: `src/main/java/br/com/stockshift/mapper/TransferMapper.java`
- Test: `src/test/java/br/com/stockshift/mapper/TransferMapperTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.mapper;

import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransferMapperTest {

    private TransferMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TransferMapper();
    }

    @Test
    void shouldMapTransferToResponse() {
        // Given
        Warehouse source = new Warehouse();
        source.setId(UUID.randomUUID());
        source.setName("Source WH");
        source.setCode("WH01");

        Warehouse destination = new Warehouse();
        destination.setId(UUID.randomUUID());
        destination.setName("Dest WH");
        destination.setCode("WH02");

        User creator = new User();
        creator.setId(UUID.randomUUID());
        creator.setFullName("John Doe");
        creator.setEmail("john@test.com");

        Transfer transfer = new Transfer();
        transfer.setId(UUID.randomUUID());
        transfer.setTransferCode("TRF-2026-00001");
        transfer.setStatus(TransferStatus.DRAFT);
        transfer.setSourceWarehouse(source);
        transfer.setDestinationWarehouse(destination);
        transfer.setCreatedBy(creator);
        transfer.setCreatedAt(LocalDateTime.now());

        // When
        TransferResponse response = mapper.toResponse(transfer, TransferRole.OUTBOUND, List.of(TransferAction.DISPATCH));

        // Then
        assertThat(response.getId()).isEqualTo(transfer.getId());
        assertThat(response.getTransferCode()).isEqualTo("TRF-2026-00001");
        assertThat(response.getStatus()).isEqualTo(TransferStatus.DRAFT);
        assertThat(response.getSourceWarehouse().getName()).isEqualTo("Source WH");
        assertThat(response.getDirection()).isEqualTo(TransferRole.OUTBOUND);
        assertThat(response.getAllowedActions()).containsExactly(TransferAction.DISPATCH);
    }

    @Test
    void shouldMapWarehouseToSummary() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setName("Test WH");
        warehouse.setCode("WH01");

        WarehouseSummary summary = mapper.toWarehouseSummary(warehouse);

        assertThat(summary.getId()).isEqualTo(warehouse.getId());
        assertThat(summary.getName()).isEqualTo("Test WH");
        assertThat(summary.getCode()).isEqualTo("WH01");
    }

    @Test
    void shouldMapUserToSummary() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Jane Doe");
        user.setEmail("jane@test.com");

        UserSummary summary = mapper.toUserSummary(user);

        assertThat(summary.getId()).isEqualTo(user.getId());
        assertThat(summary.getName()).isEqualTo("Jane Doe");
        assertThat(summary.getEmail()).isEqualTo("jane@test.com");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferMapperTest -i`
Expected: FAIL with "cannot find symbol: class TransferMapper"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.mapper;

import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TransferMapper {

    public TransferResponse toResponse(Transfer transfer, TransferRole direction, List<TransferAction> allowedActions) {
        List<TransferItemResponse> items = transfer.getItems() != null
            ? transfer.getItems().stream().map(this::toItemResponse).collect(Collectors.toList())
            : List.of();

        BigDecimal totalExpected = items.stream()
            .map(TransferItemResponse::getExpectedQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReceived = items.stream()
            .map(i -> i.getReceivedQuantity() != null ? i.getReceivedQuantity() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int itemsValidated = (int) items.stream()
            .filter(i -> i.getReceivedQuantity() != null)
            .count();

        boolean hasDiscrepancy = items.stream()
            .anyMatch(i -> i.getReceivedQuantity() != null &&
                i.getReceivedQuantity().compareTo(i.getExpectedQuantity()) != 0);

        return TransferResponse.builder()
            .id(transfer.getId())
            .transferCode(transfer.getTransferCode())
            .status(transfer.getStatus())
            .sourceWarehouse(toWarehouseSummary(transfer.getSourceWarehouse()))
            .destinationWarehouse(toWarehouseSummary(transfer.getDestinationWarehouse()))
            .items(items)
            .direction(direction)
            .allowedActions(allowedActions)
            .totalItems(items.size())
            .totalExpectedQuantity(totalExpected)
            .totalReceivedQuantity(totalReceived)
            .itemsValidated(itemsValidated)
            .hasDiscrepancy(hasDiscrepancy)
            .createdBy(toUserSummary(transfer.getCreatedBy()))
            .createdAt(transfer.getCreatedAt())
            .dispatchedBy(toUserSummary(transfer.getDispatchedBy()))
            .dispatchedAt(transfer.getDispatchedAt())
            .validationStartedBy(toUserSummary(transfer.getValidationStartedBy()))
            .validationStartedAt(transfer.getValidationStartedAt())
            .completedBy(toUserSummary(transfer.getCompletedBy()))
            .completedAt(transfer.getCompletedAt())
            .cancelledBy(toUserSummary(transfer.getCancelledBy()))
            .cancelledAt(transfer.getCancelledAt())
            .cancellationReason(transfer.getCancellationReason())
            .notes(transfer.getNotes())
            .build();
    }

    public TransferSummaryResponse toSummaryResponse(Transfer transfer, TransferRole direction, List<TransferAction> allowedActions) {
        BigDecimal totalQuantity = transfer.getItems() != null
            ? transfer.getItems().stream()
                .map(TransferItem::getExpectedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
            : BigDecimal.ZERO;

        return TransferSummaryResponse.builder()
            .id(transfer.getId())
            .transferCode(transfer.getTransferCode())
            .status(transfer.getStatus())
            .sourceWarehouse(toWarehouseSummary(transfer.getSourceWarehouse()))
            .destinationWarehouse(toWarehouseSummary(transfer.getDestinationWarehouse()))
            .direction(direction)
            .allowedActions(allowedActions)
            .itemCount(transfer.getItems() != null ? transfer.getItems().size() : 0)
            .totalQuantity(totalQuantity)
            .createdAt(transfer.getCreatedAt())
            .dispatchedAt(transfer.getDispatchedAt())
            .completedAt(transfer.getCompletedAt())
            .build();
    }

    public TransferItemResponse toItemResponse(TransferItem item) {
        return TransferItemResponse.builder()
            .id(item.getId())
            .productId(item.getProduct().getId())
            .productName(item.getProduct().getName())
            .productSku(item.getProduct().getSku())
            .sourceBatchId(item.getSourceBatch().getId())
            .sourceBatchCode(item.getSourceBatch().getBatchCode())
            .destinationBatchId(item.getDestinationBatch() != null ? item.getDestinationBatch().getId() : null)
            .destinationBatchCode(item.getDestinationBatch() != null ? item.getDestinationBatch().getBatchCode() : null)
            .expectedQuantity(item.getExpectedQuantity())
            .receivedQuantity(item.getReceivedQuantity())
            .status(item.getStatus())
            .build();
    }

    public WarehouseSummary toWarehouseSummary(Warehouse warehouse) {
        if (warehouse == null) return null;
        return WarehouseSummary.builder()
            .id(warehouse.getId())
            .name(warehouse.getName())
            .code(warehouse.getCode())
            .build();
    }

    public UserSummary toUserSummary(User user) {
        if (user == null) return null;
        return UserSummary.builder()
            .id(user.getId())
            .name(user.getFullName())
            .email(user.getEmail())
            .build();
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferMapperTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/mapper/TransferMapper.java src/test/java/br/com/stockshift/mapper/TransferMapperTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferMapper for entity-to-DTO conversion

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Implement createTransfer in TransferService

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/transfer/TransferService.java`
- Modify: `src/test/java/br/com/stockshift/service/transfer/TransferServiceTest.java`

**Step 1: Write the failing test**

```java
@Nested
class CreateTransfer {

    @Test
    void shouldCreateTransferSuccessfully() {
        CreateTransferRequest request = new CreateTransferRequest();
        request.setSourceWarehouseId(sourceWarehouseId);
        request.setDestinationWarehouseId(destinationWarehouseId);
        request.setNotes("Test transfer");

        TransferItemRequest itemRequest = new TransferItemRequest();
        itemRequest.setProductId(UUID.randomUUID());
        itemRequest.setBatchId(UUID.randomUUID());
        itemRequest.setQuantity(new BigDecimal("50"));
        request.setItems(List.of(itemRequest));

        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        when(productRepository.findById(any())).thenReturn(Optional.of(new Product()));
        when(batchRepository.findById(any())).thenReturn(Optional.of(new Batch()));
        when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
        doNothing().when(securityService).validateSourceWarehouseAccess(any());
        when(transferRepository.save(any())).thenAnswer(inv -> {
            Transfer t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        Transfer result = transferService.createTransfer(request, user);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.DRAFT);
        assertThat(result.getTransferCode()).startsWith("TRF-");
        assertThat(result.getSourceWarehouse().getId()).isEqualTo(sourceWarehouseId);
        assertThat(result.getCreatedBy()).isEqualTo(user);
    }

    @Test
    void shouldRejectSameSourceAndDestination() {
        CreateTransferRequest request = new CreateTransferRequest();
        request.setSourceWarehouseId(sourceWarehouseId);
        request.setDestinationWarehouseId(sourceWarehouseId); // Same!

        assertThatThrownBy(() -> transferService.createTransfer(request, user))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("different");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferServiceTest -i`

**Step 3: Write minimal implementation**

Add to `TransferService.java`:

```java
@Autowired
private WarehouseRepository warehouseRepository;

@Autowired
private ProductRepository productRepository;

@Autowired
private BatchRepository batchRepository;

@Transactional
public Transfer createTransfer(CreateTransferRequest request, User user) {
    log.info("Creating transfer from {} to {} by user {}",
        request.getSourceWarehouseId(), request.getDestinationWarehouseId(), user.getId());

    // Validate source != destination
    if (request.getSourceWarehouseId().equals(request.getDestinationWarehouseId())) {
        throw new BusinessException("Source and destination warehouses must be different");
    }

    // Validate source warehouse access
    securityService.validateSourceWarehouseAccess(request.getSourceWarehouseId());

    // Load warehouses
    Warehouse sourceWarehouse = warehouseRepository.findById(request.getSourceWarehouseId())
        .orElseThrow(() -> new ResourceNotFoundException("Source warehouse not found"));
    Warehouse destinationWarehouse = warehouseRepository.findById(request.getDestinationWarehouseId())
        .orElseThrow(() -> new ResourceNotFoundException("Destination warehouse not found"));

    // Create transfer
    Transfer transfer = new Transfer();
    transfer.setTenantId(warehouseAccessService.getTenantId());
    transfer.setTransferCode(generateTransferCode());
    transfer.setStatus(TransferStatus.DRAFT);
    transfer.setSourceWarehouse(sourceWarehouse);
    transfer.setDestinationWarehouse(destinationWarehouse);
    transfer.setCreatedBy(user);
    transfer.setCreatedAt(LocalDateTime.now());
    transfer.setNotes(request.getNotes());

    // Add items
    for (TransferItemRequest itemRequest : request.getItems()) {
        Product product = productRepository.findById(itemRequest.getProductId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.getProductId()));
        Batch batch = batchRepository.findById(itemRequest.getBatchId())
            .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + itemRequest.getBatchId()));

        TransferItem item = new TransferItem();
        item.setTenantId(transfer.getTenantId());
        item.setProduct(product);
        item.setSourceBatch(batch);
        item.setExpectedQuantity(itemRequest.getQuantity());
        item.setStatus(TransferItemStatus.PENDING);
        transfer.addItem(item);
    }

    transfer = transferRepository.save(transfer);
    log.info("Transfer {} created successfully", transfer.getTransferCode());

    return transfer;
}

private String generateTransferCode() {
    String year = String.valueOf(LocalDateTime.now().getYear());
    long count = transferRepository.count() + 1;
    return String.format("TRF-%s-%05d", year, count);
}
```

**Step 4: Run test to verify it passes**

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferService.java src/test/java/br/com/stockshift/service/transfer/TransferServiceTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): implement createTransfer with validation and code generation

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Implement Ledger Logic for Dispatch

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/transfer/TransferService.java`
- Modify: `src/test/java/br/com/stockshift/service/transfer/TransferServiceTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldCreateLedgerEntriesOnDispatch() {
    transfer.setStatus(TransferStatus.DRAFT);
    TransferItem item = new TransferItem();
    item.setId(UUID.randomUUID());
    item.setProduct(new Product());
    item.setSourceBatch(sourceBatch);
    item.setExpectedQuantity(new BigDecimal("50"));
    transfer.setItems(List.of(item));

    sourceBatch.setQuantity(new BigDecimal("100"));

    when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
    when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
    doNothing().when(securityService).validateAction(any(), any());
    when(securityService.determineUserRole(transfer)).thenReturn(TransferRole.OUTBOUND);
    when(stateMachine.transition(any(), any(), any())).thenReturn(TransferStatus.IN_TRANSIT);
    when(batchRepository.findByIdForUpdate(any())).thenReturn(Optional.of(sourceBatch));
    when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Transfer result = transferService.dispatch(transferId, user);

    // Verify ledger entries were created
    verify(inventoryLedgerRepository, times(2)).save(ledgerCaptor.capture());
    List<InventoryLedger> ledgerEntries = ledgerCaptor.getAllValues();

    assertThat(ledgerEntries).hasSize(2);
    assertThat(ledgerEntries.get(0).getEntryType()).isEqualTo(LedgerEntryType.TRANSFER_OUT);
    assertThat(ledgerEntries.get(1).getEntryType()).isEqualTo(LedgerEntryType.TRANSFER_IN_TRANSIT);

    // Verify TransferInTransit was created
    verify(transferInTransitRepository).save(any());

    // Verify batch was updated
    assertThat(sourceBatch.getQuantity()).isEqualTo(new BigDecimal("50"));
}
```

**Step 2: Run test to verify it fails**

**Step 3: Update dispatch implementation**

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public Transfer dispatch(UUID transferId, User user) {
    log.info("Dispatching transfer {} by user {}", transferId, user.getId());

    Transfer transfer = getTransferForUpdate(transferId);

    // Idempotency
    if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
        log.info("Transfer {} already dispatched, returning existing state", transferId);
        return transfer;
    }

    // Validate action
    securityService.validateAction(transfer, TransferAction.DISPATCH);

    // Lock batches in order to avoid deadlocks
    List<UUID> batchIds = transfer.getItems().stream()
        .map(item -> item.getSourceBatch().getId())
        .sorted()
        .collect(Collectors.toList());

    Map<UUID, Batch> batches = new HashMap<>();
    for (UUID batchId : batchIds) {
        Batch batch = batchRepository.findByIdForUpdate(batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + batchId));
        batches.put(batchId, batch);
    }

    // Process each item
    for (TransferItem item : transfer.getItems()) {
        Batch batch = batches.get(item.getSourceBatch().getId());

        // Validate stock
        if (batch.getQuantity().compareTo(item.getExpectedQuantity()) < 0) {
            throw new InsufficientStockException(
                String.format("Insufficient stock in batch %s. Available: %s, Required: %s",
                    batch.getBatchCode(), batch.getQuantity(), item.getExpectedQuantity())
            );
        }

        // Create TRANSFER_OUT ledger entry
        InventoryLedger outEntry = new InventoryLedger();
        outEntry.setTenantId(transfer.getTenantId());
        outEntry.setWarehouseId(transfer.getSourceWarehouse().getId());
        outEntry.setProductId(item.getProduct().getId());
        outEntry.setBatchId(batch.getId());
        outEntry.setEntryType(LedgerEntryType.TRANSFER_OUT);
        outEntry.setQuantity(item.getExpectedQuantity());
        outEntry.setBalanceAfter(batch.getQuantity().subtract(item.getExpectedQuantity()));
        outEntry.setReferenceType("TRANSFER");
        outEntry.setReferenceId(transfer.getId());
        outEntry.setTransferItemId(item.getId());
        outEntry.setCreatedBy(user.getId());
        inventoryLedgerRepository.save(outEntry);

        // Create TRANSFER_IN_TRANSIT ledger entry (virtual)
        InventoryLedger transitEntry = new InventoryLedger();
        transitEntry.setTenantId(transfer.getTenantId());
        transitEntry.setWarehouseId(null); // Virtual
        transitEntry.setProductId(item.getProduct().getId());
        transitEntry.setBatchId(null);
        transitEntry.setEntryType(LedgerEntryType.TRANSFER_IN_TRANSIT);
        transitEntry.setQuantity(item.getExpectedQuantity());
        transitEntry.setBalanceAfter(null);
        transitEntry.setReferenceType("TRANSFER");
        transitEntry.setReferenceId(transfer.getId());
        transitEntry.setTransferItemId(item.getId());
        transitEntry.setCreatedBy(user.getId());
        inventoryLedgerRepository.save(transitEntry);

        // Update batch quantity
        batch.setQuantity(batch.getQuantity().subtract(item.getExpectedQuantity()));
        batchRepository.save(batch);

        // Create TransferInTransit record
        TransferInTransit inTransit = new TransferInTransit();
        inTransit.setTenantId(transfer.getTenantId());
        inTransit.setTransfer(transfer);
        inTransit.setTransferItem(item);
        inTransit.setProduct(item.getProduct());
        inTransit.setSourceBatch(batch);
        inTransit.setQuantity(item.getExpectedQuantity());
        transferInTransitRepository.save(inTransit);
    }

    // Update transfer status
    TransferRole role = securityService.determineUserRole(transfer);
    TransferStatus newStatus = stateMachine.transition(transfer.getStatus(), TransferAction.DISPATCH, role);
    transfer.setStatus(newStatus);
    transfer.setDispatchedBy(user);
    transfer.setDispatchedAt(LocalDateTime.now());

    transfer = transferRepository.save(transfer);
    log.info("Transfer {} dispatched successfully", transferId);

    return transfer;
}
```

**Step 4: Run test to verify it passes**

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferService.java src/test/java/br/com/stockshift/service/transfer/TransferServiceTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): integrate ledger entries and TransferInTransit in dispatch

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Implement scanItem

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/transfer/TransferService.java`
- Test: `src/test/java/br/com/stockshift/service/transfer/TransferServiceTest.java`

**Step 1: Write the failing test**

```java
@Nested
class ScanItem {

    @Test
    void shouldScanItemSuccessfully() {
        transfer.setStatus(TransferStatus.VALIDATION_IN_PROGRESS);
        TransferItem item = new TransferItem();
        item.setId(UUID.randomUUID());
        Product product = new Product();
        product.setBarcode("1234567890");
        item.setProduct(product);
        item.setExpectedQuantity(new BigDecimal("50"));
        item.setReceivedQuantity(BigDecimal.ZERO);
        item.setStatus(TransferItemStatus.PENDING);
        transfer.setItems(List.of(item));

        ScanItemRequest request = new ScanItemRequest();
        request.setBarcode("1234567890");
        request.setQuantity(new BigDecimal("10"));

        when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
        when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
        doNothing().when(securityService).validateAction(any(), any());
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transfer result = transferService.scanItem(transferId, request, user);

        TransferItem scannedItem = result.getItems().get(0);
        assertThat(scannedItem.getReceivedQuantity()).isEqualTo(new BigDecimal("10"));
    }

    @Test
    void shouldRejectScanWhenNotInValidation() {
        transfer.setStatus(TransferStatus.IN_TRANSIT);

        ScanItemRequest request = new ScanItemRequest();
        request.setBarcode("1234567890");
        request.setQuantity(BigDecimal.ONE);

        when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
        when(warehouseAccessService.getTenantId()).thenReturn(tenantId);

        assertThatThrownBy(() -> transferService.scanItem(transferId, request, user))
            .isInstanceOf(InvalidTransferStateException.class);
    }
}
```

**Step 2: Run test to verify it fails**

**Step 3: Implement scanItem**

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public Transfer scanItem(UUID transferId, ScanItemRequest request, User user) {
    log.info("Scanning item {} for transfer {} by user {}", request.getBarcode(), transferId, user.getId());

    Transfer transfer = getTransferForUpdate(transferId);

    // Validate status
    if (transfer.getStatus() != TransferStatus.VALIDATION_IN_PROGRESS) {
        throw new InvalidTransferStateException(
            "Cannot scan items for transfer in status " + transfer.getStatus()
        );
    }

    // Validate action
    securityService.validateAction(transfer, TransferAction.SCAN_ITEM);

    // Find item by barcode
    TransferItem item = transfer.getItems().stream()
        .filter(i -> i.getProduct().getBarcode() != null &&
                     i.getProduct().getBarcode().equals(request.getBarcode()))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            "No item found with barcode: " + request.getBarcode()
        ));

    // Update received quantity
    BigDecimal currentReceived = item.getReceivedQuantity() != null
        ? item.getReceivedQuantity()
        : BigDecimal.ZERO;
    BigDecimal newReceived = currentReceived.add(request.getQuantity());
    item.setReceivedQuantity(newReceived);

    // Update item status
    if (newReceived.compareTo(item.getExpectedQuantity()) == 0) {
        item.setStatus(TransferItemStatus.RECEIVED);
    } else if (newReceived.compareTo(item.getExpectedQuantity()) < 0) {
        item.setStatus(TransferItemStatus.PARTIAL);
    } else {
        item.setStatus(TransferItemStatus.EXCESS);
    }

    transfer = transferRepository.save(transfer);
    log.info("Item scanned successfully for transfer {}", transferId);

    return transfer;
}
```

**Step 4: Run test to verify it passes**

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferService.java src/test/java/br/com/stockshift/service/transfer/TransferServiceTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): implement scanItem for validation workflow

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Implement completeValidation with Ledger and Mirror Batches

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/transfer/TransferService.java`
- Test: `src/test/java/br/com/stockshift/service/transfer/TransferServiceTest.java`

**Step 1: Write the failing test**

```java
@Nested
class CompleteValidation {

    @Test
    void shouldCompleteValidationWithoutDiscrepancy() {
        transfer.setStatus(TransferStatus.VALIDATION_IN_PROGRESS);
        TransferItem item = new TransferItem();
        item.setId(UUID.randomUUID());
        item.setProduct(new Product());
        item.setSourceBatch(sourceBatch);
        item.setExpectedQuantity(new BigDecimal("50"));
        item.setReceivedQuantity(new BigDecimal("50"));
        item.setStatus(TransferItemStatus.RECEIVED);
        transfer.setItems(List.of(item));

        TransferInTransit inTransit = new TransferInTransit();
        inTransit.setQuantity(new BigDecimal("50"));

        when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
        when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
        doNothing().when(securityService).validateAction(any(), any());
        when(securityService.determineUserRole(transfer)).thenReturn(TransferRole.INBOUND);
        when(stateMachine.transition(any(), any(), any())).thenReturn(TransferStatus.COMPLETED);
        when(transferInTransitRepository.findByTransferItemId(any())).thenReturn(Optional.of(inTransit));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transfer result = transferService.completeValidation(transferId, user);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        verify(inventoryLedgerRepository, times(2)).save(any()); // TRANSFER_IN + TRANSFER_TRANSIT_CONSUMED
    }

    @Test
    void shouldCompleteWithDiscrepancyOnShortage() {
        transfer.setStatus(TransferStatus.VALIDATION_IN_PROGRESS);
        TransferItem item = new TransferItem();
        item.setId(UUID.randomUUID());
        item.setProduct(new Product());
        item.setSourceBatch(sourceBatch);
        item.setExpectedQuantity(new BigDecimal("50"));
        item.setReceivedQuantity(new BigDecimal("40")); // Shortage
        item.setStatus(TransferItemStatus.PARTIAL);
        transfer.setItems(List.of(item));

        TransferInTransit inTransit = new TransferInTransit();
        inTransit.setQuantity(new BigDecimal("50"));

        when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
        when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
        doNothing().when(securityService).validateAction(any(), any());
        when(securityService.determineUserRole(transfer)).thenReturn(TransferRole.INBOUND);
        when(transferInTransitRepository.findByTransferItemId(any())).thenReturn(Optional.of(inTransit));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transfer result = transferService.completeValidation(transferId, user);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED_WITH_DISCREPANCY);
        assertThat(inTransit.getQuantity()).isEqualTo(new BigDecimal("10")); // Residual in transit
    }
}
```

**Step 2: Run test to verify it fails**

**Step 3: Implement completeValidation**

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

        // Check for discrepancy
        if (received.compareTo(item.getExpectedQuantity()) != 0) {
            hasDiscrepancy = true;
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

private Batch resolveDestinationBatch(Transfer transfer, TransferItem item) {
    Batch sourceBatch = item.getSourceBatch();
    String mirrorBatchCode = sourceBatch.getBatchCode() + "-" + transfer.getDestinationWarehouse().getCode();

    // Try to find existing compatible batch
    Optional<Batch> existing = batchRepository.findByWarehouseIdAndBatchCode(
        transfer.getDestinationWarehouse().getId(),
        mirrorBatchCode
    );

    if (existing.isPresent()) {
        return existing.get();
    }

    // Create mirror batch
    Batch newBatch = new Batch();
    newBatch.setTenantId(transfer.getTenantId());
    newBatch.setProduct(item.getProduct());
    newBatch.setWarehouse(transfer.getDestinationWarehouse());
    newBatch.setBatchCode(mirrorBatchCode);
    newBatch.setQuantity(BigDecimal.ZERO);
    newBatch.setExpirationDate(sourceBatch.getExpirationDate());
    newBatch.setManufacturingDate(sourceBatch.getManufacturingDate());
    newBatch.setOriginTransfer(transfer);
    newBatch.setOriginBatch(sourceBatch);

    return batchRepository.save(newBatch);
}
```

**Step 4: Run test to verify it passes**

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferService.java src/test/java/br/com/stockshift/service/transfer/TransferServiceTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): implement completeValidation with ledger and mirror batch creation

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Create TransferController

**Files:**
- Create: `src/main/java/br/com/stockshift/controller/TransferController.java`

**Step 1: Implement controller with all endpoints**

```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.mapper.TransferMapper;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.service.transfer.TransferSecurityService;
import br.com.stockshift.service.transfer.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stockshift/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final TransferSecurityService securityService;
    private final TransferMapper mapper;

    @PostMapping
    @PreAuthorize("hasAuthority('TRANSFER:CREATE')")
    public ResponseEntity<TransferResponse> createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.createTransfer(request, user);
        TransferRole role = securityService.determineUserRole(transfer);
        List<TransferAction> actions = calculateAllowedActions(transfer, role);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapper.toResponse(transfer, role, actions));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSFER:READ')")
    public ResponseEntity<TransferResponse> getTransfer(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.getTransfer(id);
        TransferRole role = securityService.determineUserRole(transfer);
        List<TransferAction> actions = calculateAllowedActions(transfer, role);

        return ResponseEntity.ok(mapper.toResponse(transfer, role, actions));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSFER:UPDATE')")
    public ResponseEntity<TransferResponse> updateTransfer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransferRequest request,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.updateTransfer(id, request, user);
        TransferRole role = securityService.determineUserRole(transfer);
        List<TransferAction> actions = calculateAllowedActions(transfer, role);

        return ResponseEntity.ok(mapper.toResponse(transfer, role, actions));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSFER:DELETE')")
    public ResponseEntity<Void> cancelTransfer(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal User user) {

        transferService.cancel(id, reason, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('TRANSFER:EXECUTE')")
    public ResponseEntity<TransferResponse> dispatchTransfer(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.dispatch(id, user);
        TransferRole role = securityService.determineUserRole(transfer);
        List<TransferAction> actions = calculateAllowedActions(transfer, role);

        return ResponseEntity.ok(mapper.toResponse(transfer, role, actions));
    }

    @PostMapping("/{id}/validation/start")
    @PreAuthorize("hasAuthority('TRANSFER:VALIDATE')")
    public ResponseEntity<TransferValidationResponse> startValidation(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.startValidation(id, user);
        return ResponseEntity.ok(buildValidationResponse(transfer));
    }

    @PostMapping("/{id}/validation/scan")
    @PreAuthorize("hasAuthority('TRANSFER:VALIDATE')")
    public ResponseEntity<TransferValidationResponse> scanItem(
            @PathVariable UUID id,
            @Valid @RequestBody ScanItemRequest request,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.scanItem(id, request, user);
        return ResponseEntity.ok(buildValidationResponse(transfer));
    }

    @PostMapping("/{id}/validation/complete")
    @PreAuthorize("hasAuthority('TRANSFER:VALIDATE')")
    public ResponseEntity<TransferResponse> completeValidation(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.completeValidation(id, user);
        TransferRole role = securityService.determineUserRole(transfer);
        List<TransferAction> actions = calculateAllowedActions(transfer, role);

        return ResponseEntity.ok(mapper.toResponse(transfer, role, actions));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TRANSFER:READ')")
    public ResponseEntity<Page<TransferSummaryResponse>> listTransfers(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String direction,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User user) {

        Page<Transfer> transfers = transferService.listTransfers(warehouseId, status, direction, pageable, user);

        Page<TransferSummaryResponse> response = transfers.map(transfer -> {
            TransferRole role = securityService.determineUserRole(transfer);
            List<TransferAction> actions = calculateAllowedActions(transfer, role);
            return mapper.toSummaryResponse(transfer, role, actions);
        });

        return ResponseEntity.ok(response);
    }

    private List<TransferAction> calculateAllowedActions(Transfer transfer, TransferRole role) {
        // Implementation per spec - calculate based on status and role
        return transferService.calculateAllowedActions(transfer, role);
    }

    private TransferValidationResponse buildValidationResponse(Transfer transfer) {
        TransferRole role = securityService.determineUserRole(transfer);
        List<TransferAction> actions = calculateAllowedActions(transfer, role);

        int itemsScanned = (int) transfer.getItems().stream()
            .filter(i -> i.getReceivedQuantity() != null && i.getReceivedQuantity().compareTo(java.math.BigDecimal.ZERO) > 0)
            .count();

        boolean hasDiscrepancy = transfer.getItems().stream()
            .anyMatch(i -> i.getReceivedQuantity() != null &&
                i.getReceivedQuantity().compareTo(i.getExpectedQuantity()) != 0);

        return TransferValidationResponse.builder()
            .transferId(transfer.getId())
            .status(transfer.getStatus())
            .validationStartedAt(transfer.getValidationStartedAt())
            .validationStartedBy(mapper.toUserSummary(transfer.getValidationStartedBy()))
            .items(transfer.getItems().stream().map(mapper::toItemResponse).toList())
            .totalItems(transfer.getItems().size())
            .itemsScanned(itemsScanned)
            .itemsPending(transfer.getItems().size() - itemsScanned)
            .hasDiscrepancy(hasDiscrepancy)
            .canComplete(itemsScanned > 0)
            .allowedActions(actions)
            .build();
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/TransferController.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferController with all API endpoints

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Create TransferController Integration Test

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java`

**Step 1: Implement E2E test**

```java
package br.com.stockshift.controller;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.*;
import br.com.stockshift.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransferControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private InventoryLedgerRepository ledgerRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant testTenant;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private Product product;
    private Batch batch;
    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        transferRepository.deleteAll();
        ledgerRepository.deleteAll();
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Transfer Test Tenant", "22222222000102");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder, testTenant.getId(), "transfer@test.com");
        testCategory = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Test Category");
        sourceWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Source Warehouse");
        destinationWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Destination Warehouse");
        product = TestDataFactory.createProduct(productRepository, testTenant.getId(), testCategory, "Test Product", "TEST-SKU");
        product.setBarcode("1234567890");
        productRepository.save(product);
        batch = TestDataFactory.createBatch(batchRepository, testTenant.getId(), product, sourceWarehouse, "BATCH-001", 100);
    }

    @Test
    @WithMockUser(username = "transfer@test.com")
    void shouldCompleteFullTransferFlow() throws Exception {
        // 1. Create Transfer
        CreateTransferRequest createRequest = new CreateTransferRequest();
        createRequest.setSourceWarehouseId(sourceWarehouse.getId());
        createRequest.setDestinationWarehouseId(destinationWarehouse.getId());

        TransferItemRequest itemRequest = new TransferItemRequest();
        itemRequest.setProductId(product.getId());
        itemRequest.setBatchId(batch.getId());
        itemRequest.setQuantity(new BigDecimal("50"));
        createRequest.setItems(List.of(itemRequest));

        String createResponse = mockMvc.perform(post("/stockshift/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.transferCode").exists())
            .andReturn().getResponse().getContentAsString();

        String transferId = objectMapper.readTree(createResponse).get("id").asText();

        // 2. Dispatch Transfer
        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/dispatch"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        // Verify stock was reduced
        Batch updatedBatch = batchRepository.findById(batch.getId()).orElseThrow();
        assertThat(updatedBatch.getQuantity()).isEqualByComparingTo(new BigDecimal("50"));

        // Verify ledger entries
        long ledgerCount = ledgerRepository.countByReferenceId(java.util.UUID.fromString(transferId));
        assertThat(ledgerCount).isEqualTo(2); // TRANSFER_OUT + TRANSFER_IN_TRANSIT

        // 3. Start Validation
        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/validation/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VALIDATION_IN_PROGRESS"));

        // 4. Scan Item
        ScanItemRequest scanRequest = new ScanItemRequest();
        scanRequest.setBarcode("1234567890");
        scanRequest.setQuantity(new BigDecimal("50"));

        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/validation/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(scanRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itemsScanned").value(1));

        // 5. Complete Validation
        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/validation/complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Verify destination batch was created
        List<Batch> destinationBatches = batchRepository.findByWarehouseId(destinationWarehouse.getId());
        assertThat(destinationBatches).hasSize(1);
        assertThat(destinationBatches.get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("50"));

        // Verify final ledger entries (4 total: OUT, IN_TRANSIT, IN, TRANSIT_CONSUMED)
        long finalLedgerCount = ledgerRepository.countByReferenceId(java.util.UUID.fromString(transferId));
        assertThat(finalLedgerCount).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = "transfer@test.com")
    void shouldCompleteWithDiscrepancyOnShortage() throws Exception {
        // Create and dispatch transfer
        CreateTransferRequest createRequest = new CreateTransferRequest();
        createRequest.setSourceWarehouseId(sourceWarehouse.getId());
        createRequest.setDestinationWarehouseId(destinationWarehouse.getId());

        TransferItemRequest itemRequest = new TransferItemRequest();
        itemRequest.setProductId(product.getId());
        itemRequest.setBatchId(batch.getId());
        itemRequest.setQuantity(new BigDecimal("50"));
        createRequest.setItems(List.of(itemRequest));

        String createResponse = mockMvc.perform(post("/stockshift/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String transferId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/dispatch"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/validation/start"))
            .andExpect(status().isOk());

        // Scan only 40 (expected 50)
        ScanItemRequest scanRequest = new ScanItemRequest();
        scanRequest.setBarcode("1234567890");
        scanRequest.setQuantity(new BigDecimal("40"));

        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/validation/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(scanRequest)))
            .andExpect(status().isOk());

        // Complete - should have discrepancy
        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/validation/complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED_WITH_DISCREPANCY"))
            .andExpect(jsonPath("$.hasDiscrepancy").value(true));
    }
}
```

**Step 2: Run test to verify it passes**

Run: `./gradlew test --tests TransferControllerIntegrationTest -i`

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java
git commit -m "$(cat <<'EOF'
test(transfer): add E2E integration tests for complete transfer flow

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Run Full Test Suite

**Step 1: Run all tests**

Run: `./gradlew test -i`
Expected: All tests PASS

**Step 2: Fix any failures**

If any tests fail, fix them before proceeding.

---

## Task 11: Final Verification and Tag

**Step 1: Verify git status is clean**

Run: `git status`
Expected: Clean working directory

**Step 2: Review commit log**

Run: `git log --oneline -15`

**Step 3: Tag the phase completion**

```bash
git tag -a v0.3.0-transfer-phase3 -m "Transfer Refactoring Phase 3: API Redesign & Ledger Integration complete"
```

---

## Summary

Phase 3 implements the full Transfer API and Ledger integration:

| Component | Files Created/Modified |
|-----------|------------------------|
| Request DTOs | `CreateTransferRequest`, `TransferItemRequest`, `UpdateTransferRequest`, `ScanItemRequest` |
| Response DTOs | `TransferResponse`, `TransferItemResponse`, `TransferSummaryResponse`, `TransferValidationResponse`, `WarehouseSummary`, `UserSummary` |
| Mapper | `TransferMapper` |
| Service | `TransferService` (create, update, dispatch w/ ledger, scan, complete w/ ledger) |
| Controller | `TransferController` (all endpoints per spec) |
| Integration Tests | `TransferControllerIntegrationTest` |

**Invariants Enforced:**
- I7: Ledger is append-only (INSERT only, trigger prevents UPDATE/DELETE)
- I8: Every ledger entry has referenceType/referenceId
- I9: Source and destination must be different
- I10: Batch must have sufficient stock for dispatch

**Key Patterns:**
- Mirror-batch creation at destination
- Pessimistic locking with ordered batch locks
- Idempotent operations
- Atomic transactions with REPEATABLE_READ isolation

**Next Phase:** Phase 4 will implement robustness features (event sourcing, scan idempotency key, discrepancy resolution).
