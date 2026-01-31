# Transfer Operational Robustness (Phase 5) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate double-execution bugs, handle retries gracefully, and provide complete audit trail for all transfer operations.

**Architecture:** Add TransferEvent entity for event sourcing light, ScanLog for scan idempotency, and enhance GlobalExceptionHandler for concurrency conflicts. The Transfer entity already has @Version for optimistic locking; we'll add pessimistic locking via findByIdForUpdate and proper exception handling.

**Tech Stack:** Spring Boot, JPA/Hibernate, PostgreSQL, Flyway migrations

---

## Current State Analysis

Based on codebase exploration:

| Feature | Current State | Gap |
|---------|---------------|-----|
| **Atomicity** | `@Transactional` with `REPEATABLE_READ` on critical methods | ✅ Already implemented |
| **Optimistic Locking** | Transfer has `@Version` field | ✅ Already implemented |
| **Pessimistic Locking** | `findByIdForUpdate` exists in repositories | ✅ Already implemented |
| **Idempotency (state-based)** | dispatch/completeValidation check current status | ✅ Already implemented |
| **Idempotency (key-based)** | Not implemented for scan operations | ❌ Missing |
| **Concurrency Exception Handling** | Not in GlobalExceptionHandler | ❌ Missing |
| **TransferEvent (audit)** | Not implemented | ❌ Missing |
| **History Endpoint** | Not implemented | ❌ Missing |

---

## Task 1: Add Concurrency Exception Handlers

**Files:**
- Modify: `src/main/java/br/com/stockshift/exception/GlobalExceptionHandler.java`

**Step 1: Write failing test for optimistic lock exception**

Create: `src/test/java/br/com/stockshift/exception/GlobalExceptionHandlerTest.java`

```java
package br.com.stockshift.exception;

import br.com.stockshift.security.ratelimit.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private RateLimitService rateLimitService;

    private GlobalExceptionHandler handler;
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(rateLimitService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/stockshift/transfers/123/dispatch");
        webRequest = new ServletWebRequest(request);
    }

    @Test
    void handleOptimisticLockingFailure_shouldReturn409Conflict() {
        OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Row was updated");

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockingFailure(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Concurrent Modification");
        assertThat(response.getBody().getMessage()).contains("modified by another user");
    }

    @Test
    void handlePessimisticLockingFailure_shouldReturn409Conflict() {
        PessimisticLockingFailureException ex = new PessimisticLockingFailureException("Could not acquire lock");

        ResponseEntity<ErrorResponse> response = handler.handlePessimisticLockingFailure(ex, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Resource Locked");
        assertThat(response.getBody().getMessage()).contains("being processed");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests GlobalExceptionHandlerTest -i`
Expected: FAIL with "cannot find symbol: method handleOptimisticLockingFailure"

**Step 3: Implement exception handlers**

Add to `GlobalExceptionHandler.java` after the existing imports:

```java
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
```

Add these methods before the `handleGlobalException` method:

```java
@ExceptionHandler(OptimisticLockingFailureException.class)
public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
        OptimisticLockingFailureException ex,
        WebRequest request
) {
    log.warn("Optimistic locking failure: {}", ex.getMessage());

    ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.CONFLICT.value())
            .error("Concurrent Modification")
            .message("This resource was modified by another user. Please refresh and try again.")
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

    return new ResponseEntity<>(error, HttpStatus.CONFLICT);
}

@ExceptionHandler(PessimisticLockingFailureException.class)
public ResponseEntity<ErrorResponse> handlePessimisticLockingFailure(
        PessimisticLockingFailureException ex,
        WebRequest request
) {
    log.warn("Pessimistic locking failure: {}", ex.getMessage());

    ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.CONFLICT.value())
            .error("Resource Locked")
            .message("This resource is currently being processed. Please try again in a moment.")
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

    return new ResponseEntity<>(error, HttpStatus.CONFLICT);
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests GlobalExceptionHandlerTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/exception/GlobalExceptionHandler.java src/test/java/br/com/stockshift/exception/GlobalExceptionHandlerTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add concurrency exception handlers

Add handlers for OptimisticLockingFailureException and
PessimisticLockingFailureException returning 409 Conflict
with user-friendly messages.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Create TransferEventType Enum

**Files:**
- Create: `src/main/java/br/com/stockshift/model/enums/TransferEventType.java`

**Step 1: Create enum file**

```java
package br.com.stockshift.model.enums;

public enum TransferEventType {
    CREATED,
    UPDATED,
    DISPATCHED,
    VALIDATION_STARTED,
    ITEM_SCANNED,
    COMPLETED,
    COMPLETED_WITH_DISCREPANCY,
    CANCELLED,
    DISCREPANCY_RESOLVED
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/model/enums/TransferEventType.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferEventType enum

Add enum for tracking all transfer lifecycle events
for audit trail purposes.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Create TransferEvent Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/TransferEvent.java`

**Step 1: Create entity file**

```java
package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.TransferEventType;
import br.com.stockshift.model.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transfer_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private TransferEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 50)
    private TransferStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 50)
    private TransferStatus toStatus;

    @Column(name = "performed_by", nullable = false)
    private UUID performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/TransferEvent.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferEvent entity for audit trail

Entity stores all transfer lifecycle events with metadata
for complete audit history.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Create TransferEvent Database Migration

**Files:**
- Create: `src/main/resources/db/migration/V37__create_transfer_events.sql`

**Step 1: Create migration file**

```sql
-- V37: Create transfer_events table for audit trail

CREATE TABLE transfer_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    transfer_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    performed_by UUID NOT NULL,
    performed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    metadata JSONB,

    CONSTRAINT fk_transfer_events_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id) ON DELETE CASCADE,
    CONSTRAINT chk_event_type CHECK (event_type IN (
        'CREATED', 'UPDATED', 'DISPATCHED', 'VALIDATION_STARTED',
        'ITEM_SCANNED', 'COMPLETED', 'COMPLETED_WITH_DISCREPANCY',
        'CANCELLED', 'DISCREPANCY_RESOLVED'
    ))
);

CREATE INDEX idx_transfer_events_transfer ON transfer_events(transfer_id);
CREATE INDEX idx_transfer_events_tenant ON transfer_events(tenant_id);
CREATE INDEX idx_transfer_events_performed_at ON transfer_events(performed_at DESC);
```

**Step 2: Verify migration syntax**

Run: `./gradlew flywayValidate` (or run the app to check)
Expected: Valid migration

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V37__create_transfer_events.sql
git commit -m "$(cat <<'EOF'
feat(transfer): add transfer_events table migration

Creates table with indexes for storing transfer lifecycle
events for audit purposes.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Create TransferEventRepository

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/TransferEventRepository.java`

**Step 1: Create repository interface**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferEventRepository extends JpaRepository<TransferEvent, UUID> {

    @Query("SELECT e FROM TransferEvent e WHERE e.transferId = :transferId ORDER BY e.performedAt ASC")
    List<TransferEvent> findByTransferIdOrderByPerformedAtAsc(@Param("transferId") UUID transferId);

    @Query("SELECT e FROM TransferEvent e WHERE e.tenantId = :tenantId AND e.transferId = :transferId ORDER BY e.performedAt ASC")
    List<TransferEvent> findByTenantIdAndTransferIdOrderByPerformedAtAsc(
            @Param("tenantId") UUID tenantId,
            @Param("transferId") UUID transferId);
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/TransferEventRepository.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferEventRepository

Repository for querying transfer events by transfer ID
with tenant isolation.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Create TransferEventPublisher Service

**Files:**
- Create: `src/main/java/br/com/stockshift/service/transfer/TransferEventPublisher.java`
- Test: `src/test/java/br/com/stockshift/service/transfer/TransferEventPublisherTest.java`

**Step 1: Write failing test**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferEvent;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.enums.TransferEventType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.TransferEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferEventPublisherTest {

    @Mock
    private TransferEventRepository eventRepository;

    private TransferEventPublisher publisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new TransferEventPublisher(eventRepository, objectMapper);
    }

    @Test
    void publish_shouldSaveEventWithCorrectFields() {
        // Arrange
        Transfer transfer = new Transfer();
        transfer.setId(UUID.randomUUID());
        transfer.setTenantId(UUID.randomUUID());
        transfer.setStatus(TransferStatus.IN_TRANSIT);

        User user = new User();
        user.setId(UUID.randomUUID());

        // Act
        publisher.publish(
            transfer,
            TransferEventType.DISPATCHED,
            TransferStatus.DRAFT,
            user,
            Map.of("itemCount", 5, "totalQuantity", 100)
        );

        // Assert
        ArgumentCaptor<TransferEvent> captor = ArgumentCaptor.forClass(TransferEvent.class);
        verify(eventRepository).save(captor.capture());

        TransferEvent saved = captor.getValue();
        assertThat(saved.getTransferId()).isEqualTo(transfer.getId());
        assertThat(saved.getTenantId()).isEqualTo(transfer.getTenantId());
        assertThat(saved.getEventType()).isEqualTo(TransferEventType.DISPATCHED);
        assertThat(saved.getFromStatus()).isEqualTo(TransferStatus.DRAFT);
        assertThat(saved.getToStatus()).isEqualTo(TransferStatus.IN_TRANSIT);
        assertThat(saved.getPerformedBy()).isEqualTo(user.getId());
        assertThat(saved.getMetadata()).contains("itemCount");
    }

    @Test
    void publish_withoutMetadata_shouldSaveEventWithNullMetadata() {
        // Arrange
        Transfer transfer = new Transfer();
        transfer.setId(UUID.randomUUID());
        transfer.setTenantId(UUID.randomUUID());
        transfer.setStatus(TransferStatus.CANCELLED);

        User user = new User();
        user.setId(UUID.randomUUID());

        // Act
        publisher.publish(
            transfer,
            TransferEventType.CANCELLED,
            TransferStatus.DRAFT,
            user,
            null
        );

        // Assert
        ArgumentCaptor<TransferEvent> captor = ArgumentCaptor.forClass(TransferEvent.class);
        verify(eventRepository).save(captor.capture());

        TransferEvent saved = captor.getValue();
        assertThat(saved.getMetadata()).isNull();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferEventPublisherTest -i`
Expected: FAIL with "cannot find symbol: class TransferEventPublisher"

**Step 3: Implement TransferEventPublisher**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferEvent;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.enums.TransferEventType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.TransferEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferEventPublisher {

    private final TransferEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public void publish(
            Transfer transfer,
            TransferEventType eventType,
            TransferStatus fromStatus,
            User performedBy,
            Map<String, Object> metadata
    ) {
        String metadataJson = null;
        if (metadata != null) {
            try {
                metadataJson = objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize event metadata: {}", e.getMessage());
            }
        }

        TransferEvent event = TransferEvent.builder()
                .tenantId(transfer.getTenantId())
                .transferId(transfer.getId())
                .eventType(eventType)
                .fromStatus(fromStatus)
                .toStatus(transfer.getStatus())
                .performedBy(performedBy.getId())
                .performedAt(LocalDateTime.now())
                .metadata(metadataJson)
                .build();

        eventRepository.save(event);
        log.debug("Published {} event for transfer {}", eventType, transfer.getId());
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferEventPublisherTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferEventPublisher.java src/test/java/br/com/stockshift/service/transfer/TransferEventPublisherTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferEventPublisher service

Publishes transfer lifecycle events to the database
for audit trail purposes.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Integrate Event Publishing in TransferService

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/transfer/TransferService.java`

**Step 1: Add dependency injection**

Add to class fields:
```java
private final TransferEventPublisher eventPublisher;
```

**Step 2: Add event publishing to createTransfer**

After `transfer = transferRepository.save(transfer);` add:
```java
// Publish event
eventPublisher.publish(
    transfer,
    TransferEventType.CREATED,
    null,
    user,
    Map.of(
        "itemCount", transfer.getItems().size(),
        "sourceWarehouseId", transfer.getSourceWarehouse().getId().toString(),
        "destinationWarehouseId", transfer.getDestinationWarehouse().getId().toString()
    )
);
```

**Step 3: Add event publishing to dispatch**

After status change to IN_TRANSIT and before return, add:
```java
// Publish event
eventPublisher.publish(
    transfer,
    TransferEventType.DISPATCHED,
    previousStatus,
    user,
    Map.of(
        "itemCount", transfer.getItems().size(),
        "totalQuantity", transfer.getItems().stream()
            .map(TransferItem::getExpectedQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
    )
);
```

**Step 4: Add event publishing to startValidation**

After status change to VALIDATION_IN_PROGRESS and before return, add:
```java
// Publish event
eventPublisher.publish(
    transfer,
    TransferEventType.VALIDATION_STARTED,
    previousStatus,
    user,
    null
);
```

**Step 5: Add event publishing to completeValidation**

After status change to COMPLETED/COMPLETED_WITH_DISCREPANCY and before return, add:
```java
// Publish event
TransferEventType eventType = transfer.getStatus() == TransferStatus.COMPLETED_WITH_DISCREPANCY
    ? TransferEventType.COMPLETED_WITH_DISCREPANCY
    : TransferEventType.COMPLETED;

eventPublisher.publish(
    transfer,
    eventType,
    previousStatus,
    user,
    Map.of(
        "hasDiscrepancy", hasDiscrepancy,
        "itemsReceived", transfer.getItems().size()
    )
);
```

**Step 6: Add event publishing to cancel**

After status change to CANCELLED and before return, add:
```java
// Publish event
eventPublisher.publish(
    transfer,
    TransferEventType.CANCELLED,
    previousStatus,
    user,
    null
);
```

**Step 7: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 8: Run existing tests**

Run: `./gradlew test --tests "TransferService*" -i`
Expected: All tests pass

**Step 9: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferService.java
git commit -m "$(cat <<'EOF'
feat(transfer): integrate event publishing in TransferService

Publish TransferEvents for CREATED, DISPATCHED, VALIDATION_STARTED,
COMPLETED, COMPLETED_WITH_DISCREPANCY, and CANCELLED transitions.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Create Transfer History DTOs

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/transfer/TransferEventResponse.java`
- Create: `src/main/java/br/com/stockshift/dto/transfer/TransferHistoryResponse.java`

**Step 1: Create TransferEventResponse DTO**

```java
package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferEventType;
import br.com.stockshift.model.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferEventResponse {
    private UUID id;
    private TransferEventType eventType;
    private TransferStatus fromStatus;
    private TransferStatus toStatus;
    private UUID performedBy;
    private String performedByName;
    private LocalDateTime performedAt;
    private Map<String, Object> metadata;
}
```

**Step 2: Create TransferHistoryResponse DTO**

```java
package br.com.stockshift.dto.transfer;

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
public class TransferHistoryResponse {
    private UUID transferId;
    private String transferCode;
    private List<TransferEventResponse> events;
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/transfer/TransferEventResponse.java src/main/java/br/com/stockshift/dto/transfer/TransferHistoryResponse.java
git commit -m "$(cat <<'EOF'
feat(transfer): add history response DTOs

Add TransferEventResponse and TransferHistoryResponse
for the history endpoint.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Create TransferHistoryService

**Files:**
- Create: `src/main/java/br/com/stockshift/service/transfer/TransferHistoryService.java`
- Test: `src/test/java/br/com/stockshift/service/transfer/TransferHistoryServiceTest.java`

**Step 1: Write failing test**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.dto.transfer.TransferHistoryResponse;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferEvent;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.enums.TransferEventType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.TransferEventRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.service.WarehouseAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferHistoryServiceTest {

    @Mock
    private TransferEventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WarehouseAccessService warehouseAccessService;

    private TransferHistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = new TransferHistoryService(
            eventRepository,
            userRepository,
            warehouseAccessService,
            new ObjectMapper()
        );
    }

    @Test
    void getHistory_shouldReturnEventsInOrder() {
        // Arrange
        UUID tenantId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Transfer transfer = new Transfer();
        transfer.setId(transferId);
        transfer.setTenantId(tenantId);
        transfer.setTransferCode("TRF-2026-00001");

        User user = new User();
        user.setId(userId);
        user.setName("Test User");

        TransferEvent event1 = TransferEvent.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .transferId(transferId)
            .eventType(TransferEventType.CREATED)
            .fromStatus(null)
            .toStatus(TransferStatus.DRAFT)
            .performedBy(userId)
            .performedAt(LocalDateTime.now().minusHours(2))
            .build();

        TransferEvent event2 = TransferEvent.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .transferId(transferId)
            .eventType(TransferEventType.DISPATCHED)
            .fromStatus(TransferStatus.DRAFT)
            .toStatus(TransferStatus.IN_TRANSIT)
            .performedBy(userId)
            .performedAt(LocalDateTime.now().minusHours(1))
            .metadata("{\"itemCount\":5}")
            .build();

        when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
        when(eventRepository.findByTenantIdAndTransferIdOrderByPerformedAtAsc(tenantId, transferId))
            .thenReturn(List.of(event1, event2));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        TransferHistoryResponse response = historyService.getHistory(transfer);

        // Assert
        assertThat(response.getTransferId()).isEqualTo(transferId);
        assertThat(response.getTransferCode()).isEqualTo("TRF-2026-00001");
        assertThat(response.getEvents()).hasSize(2);
        assertThat(response.getEvents().get(0).getEventType()).isEqualTo(TransferEventType.CREATED);
        assertThat(response.getEvents().get(1).getEventType()).isEqualTo(TransferEventType.DISPATCHED);
        assertThat(response.getEvents().get(1).getPerformedByName()).isEqualTo("Test User");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferHistoryServiceTest -i`
Expected: FAIL with "cannot find symbol: class TransferHistoryService"

**Step 3: Implement TransferHistoryService**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.dto.transfer.TransferEventResponse;
import br.com.stockshift.dto.transfer.TransferHistoryResponse;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferEvent;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.TransferEventRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.service.WarehouseAccessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferHistoryService {

    private final TransferEventRepository eventRepository;
    private final UserRepository userRepository;
    private final WarehouseAccessService warehouseAccessService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public TransferHistoryResponse getHistory(Transfer transfer) {
        UUID tenantId = warehouseAccessService.getTenantId();

        List<TransferEvent> events = eventRepository
            .findByTenantIdAndTransferIdOrderByPerformedAtAsc(tenantId, transfer.getId());

        List<TransferEventResponse> eventResponses = events.stream()
            .map(this::toEventResponse)
            .collect(Collectors.toList());

        return TransferHistoryResponse.builder()
            .transferId(transfer.getId())
            .transferCode(transfer.getTransferCode())
            .events(eventResponses)
            .build();
    }

    private TransferEventResponse toEventResponse(TransferEvent event) {
        String userName = userRepository.findById(event.getPerformedBy())
            .map(User::getName)
            .orElse("Unknown User");

        Map<String, Object> metadata = null;
        if (event.getMetadata() != null) {
            try {
                metadata = objectMapper.readValue(
                    event.getMetadata(),
                    new TypeReference<Map<String, Object>>() {}
                );
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse event metadata: {}", e.getMessage());
            }
        }

        return TransferEventResponse.builder()
            .id(event.getId())
            .eventType(event.getEventType())
            .fromStatus(event.getFromStatus())
            .toStatus(event.getToStatus())
            .performedBy(event.getPerformedBy())
            .performedByName(userName)
            .performedAt(event.getPerformedAt())
            .metadata(metadata)
            .build();
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferHistoryServiceTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferHistoryService.java src/test/java/br/com/stockshift/service/transfer/TransferHistoryServiceTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferHistoryService

Service to retrieve transfer event history with
user name resolution and metadata parsing.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Add History Endpoint to TransferController

**Files:**
- Modify: `src/main/java/br/com/stockshift/controller/TransferController.java`

**Step 1: Add dependency injection**

Add to class fields:
```java
private final TransferHistoryService historyService;
```

**Step 2: Add history endpoint**

```java
@GetMapping("/{id}/history")
@Operation(summary = "Get transfer history", description = "Get the complete audit history of a transfer")
public ApiResponse<TransferHistoryResponse> getTransferHistory(@PathVariable UUID id) {
    log.info("Getting history for transfer {}", id);
    Transfer transfer = transferService.getTransfer(id);
    TransferHistoryResponse history = historyService.getHistory(transfer);
    return ApiResponse.success(history);
}
```

**Step 3: Add necessary imports**

```java
import br.com.stockshift.dto.transfer.TransferHistoryResponse;
import br.com.stockshift.service.transfer.TransferHistoryService;
```

**Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/TransferController.java
git commit -m "$(cat <<'EOF'
feat(transfer): add GET /transfers/{id}/history endpoint

Exposes transfer audit trail via REST API.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Create ScanLog Entity for Idempotency

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/ScanLog.java`

**Step 1: Create entity file**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scan_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "transfer_item_id", nullable = false)
    private UUID transferItemId;

    @Column(name = "barcode", nullable = false, length = 100)
    private String barcode;

    @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/ScanLog.java
git commit -m "$(cat <<'EOF'
feat(transfer): add ScanLog entity for scan idempotency

Entity stores scan operations with idempotency key
to prevent duplicate scans on retry.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Create ScanLog Database Migration

**Files:**
- Create: `src/main/resources/db/migration/V38__create_scan_logs.sql`

**Step 1: Create migration file**

```sql
-- V38: Create scan_logs table for scan idempotency

CREATE TABLE scan_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key UUID UNIQUE NOT NULL,
    tenant_id UUID NOT NULL,
    transfer_id UUID NOT NULL,
    transfer_item_id UUID NOT NULL,
    barcode VARCHAR(100) NOT NULL,
    quantity NUMERIC(15, 3) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL DEFAULT (NOW() + INTERVAL '24 hours'),

    CONSTRAINT fk_scan_logs_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id) ON DELETE CASCADE
);

CREATE INDEX idx_scan_logs_idempotency ON scan_logs(idempotency_key);
CREATE INDEX idx_scan_logs_expires ON scan_logs(expires_at);
CREATE INDEX idx_scan_logs_transfer ON scan_logs(transfer_id);
```

**Step 2: Verify migration syntax**

Run: `./gradlew flywayValidate`
Expected: Valid migration

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V38__create_scan_logs.sql
git commit -m "$(cat <<'EOF'
feat(transfer): add scan_logs table migration

Creates table for storing scan operations with TTL
for idempotency purposes.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Create ScanLogRepository

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/ScanLogRepository.java`

**Step 1: Create repository interface**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.ScanLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanLogRepository extends JpaRepository<ScanLog, UUID> {

    Optional<ScanLog> findByIdempotencyKey(UUID idempotencyKey);

    @Modifying
    @Query("DELETE FROM ScanLog s WHERE s.expiresAt < :now")
    int deleteExpiredBefore(@Param("now") LocalDateTime now);
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/ScanLogRepository.java
git commit -m "$(cat <<'EOF'
feat(transfer): add ScanLogRepository

Repository for scan idempotency with expiration cleanup.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Create ScanLog Cleanup Job

**Files:**
- Create: `src/main/java/br/com/stockshift/job/ScanLogCleanupJob.java`
- Test: `src/test/java/br/com/stockshift/job/ScanLogCleanupJobTest.java`

**Step 1: Write failing test**

```java
package br.com.stockshift.job;

import br.com.stockshift.repository.ScanLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanLogCleanupJobTest {

    @Mock
    private ScanLogRepository scanLogRepository;

    @InjectMocks
    private ScanLogCleanupJob cleanupJob;

    @Test
    void cleanup_shouldDeleteExpiredRecords() {
        // Arrange
        when(scanLogRepository.deleteExpiredBefore(any(LocalDateTime.class))).thenReturn(10);

        // Act
        cleanupJob.cleanupExpiredScanLogs();

        // Assert
        verify(scanLogRepository).deleteExpiredBefore(any(LocalDateTime.class));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests ScanLogCleanupJobTest -i`
Expected: FAIL with "cannot find symbol: class ScanLogCleanupJob"

**Step 3: Implement ScanLogCleanupJob**

```java
package br.com.stockshift.job;

import br.com.stockshift.repository.ScanLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScanLogCleanupJob {

    private final ScanLogRepository scanLogRepository;

    @Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
    @Transactional
    public void cleanupExpiredScanLogs() {
        log.info("Starting scan log cleanup job");
        int deletedCount = scanLogRepository.deleteExpiredBefore(LocalDateTime.now());
        log.info("Scan log cleanup completed. Deleted {} expired records", deletedCount);
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests ScanLogCleanupJobTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/job/ScanLogCleanupJob.java src/test/java/br/com/stockshift/job/ScanLogCleanupJobTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add ScanLogCleanupJob

Scheduled job to clean up expired scan log entries
daily at 3 AM.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: Update ScanItemRequest with IdempotencyKey

**Files:**
- Modify: `src/main/java/br/com/stockshift/dto/transfer/ScanItemRequest.java`

**Step 1: Read current file structure and add idempotencyKey field**

Add to the DTO:
```java
@Schema(description = "Client-generated idempotency key for retry safety")
private UUID idempotencyKey;
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/transfer/ScanItemRequest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add idempotencyKey to ScanItemRequest

Optional client-generated key for preventing duplicate
scan processing on network retries.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 16: Integrate Scan Idempotency in TransferService

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/transfer/TransferService.java`

**Step 1: Add dependency injection**

Add to class fields:
```java
private final ScanLogRepository scanLogRepository;
```

**Step 2: Modify scanItem method to check idempotency key**

At the beginning of the scanItem method, after getting the transfer:
```java
// Check idempotency key if provided
if (request.getIdempotencyKey() != null) {
    Optional<ScanLog> existingScan = scanLogRepository.findByIdempotencyKey(request.getIdempotencyKey());
    if (existingScan.isPresent()) {
        log.info("Scan {} already processed, returning existing state", request.getIdempotencyKey());
        // Return the current transfer item state
        TransferItem item = transfer.getItems().stream()
            .filter(i -> i.getId().equals(existingScan.get().getTransferItemId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Transfer item not found"));
        return item;
    }
}
```

**Step 3: After processing the scan, log it for idempotency**

After updating the transfer item and before returning:
```java
// Log scan for idempotency
if (request.getIdempotencyKey() != null) {
    scanLogRepository.save(ScanLog.builder()
        .idempotencyKey(request.getIdempotencyKey())
        .tenantId(transfer.getTenantId())
        .transferId(transfer.getId())
        .transferItemId(item.getId())
        .barcode(request.getBarcode())
        .quantity(request.getQuantity())
        .processedAt(LocalDateTime.now())
        .expiresAt(LocalDateTime.now().plusHours(24))
        .build());
}
```

**Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Run existing tests**

Run: `./gradlew test --tests "TransferService*" -i`
Expected: All tests pass

**Step 6: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferService.java
git commit -m "$(cat <<'EOF'
feat(transfer): integrate scan idempotency in TransferService

Check and log idempotency key for scan operations to
prevent duplicate quantity additions on network retries.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 17: Add Integration Test for Idempotent Dispatch

**Files:**
- Modify: `src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java`

**Step 1: Add test for idempotent dispatch**

```java
@Test
@DisplayName("Dispatch should be idempotent - double dispatch returns same result")
void dispatchTransfer_whenCalledTwice_shouldBeIdempotent() throws Exception {
    // Arrange: Create a transfer and dispatch once
    Transfer transfer = createTestTransfer();

    // First dispatch
    mockMvc.perform(post("/stockshift/transfers/" + transfer.getId() + "/dispatch")
            .header("Authorization", "Bearer " + sourceUserToken)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));

    // Act: Dispatch again (should be idempotent)
    mockMvc.perform(post("/stockshift/transfers/" + transfer.getId() + "/dispatch")
            .header("Authorization", "Bearer " + sourceUserToken)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));

    // Assert: Only one set of ledger entries
    List<InventoryLedger> ledgerEntries = inventoryLedgerRepository.findByReferenceId(transfer.getId());
    long transferOutCount = ledgerEntries.stream()
        .filter(e -> e.getEntryType() == LedgerEntryType.TRANSFER_OUT)
        .count();
    assertThat(transferOutCount).isEqualTo(transfer.getItems().size()); // One per item, not doubled
}
```

**Step 2: Run integration test**

Run: `./gradlew test --tests "TransferControllerIntegrationTest.dispatchTransfer_whenCalledTwice_shouldBeIdempotent" -i`
Expected: PASS

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java
git commit -m "$(cat <<'EOF'
test(transfer): add integration test for idempotent dispatch

Verify that calling dispatch twice doesn't duplicate
ledger entries.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 18: Add Integration Test for Transfer History

**Files:**
- Modify: `src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java`

**Step 1: Add test for history endpoint**

```java
@Test
@DisplayName("GET /transfers/{id}/history returns event history")
void getTransferHistory_shouldReturnEventHistory() throws Exception {
    // Arrange: Create and dispatch a transfer
    Transfer transfer = createTestTransfer();

    mockMvc.perform(post("/stockshift/transfers/" + transfer.getId() + "/dispatch")
            .header("Authorization", "Bearer " + sourceUserToken)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

    // Act & Assert
    mockMvc.perform(get("/stockshift/transfers/" + transfer.getId() + "/history")
            .header("Authorization", "Bearer " + sourceUserToken)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.transferId").value(transfer.getId().toString()))
            .andExpect(jsonPath("$.data.events").isArray())
            .andExpect(jsonPath("$.data.events.length()").value(2)) // CREATED + DISPATCHED
            .andExpect(jsonPath("$.data.events[0].eventType").value("CREATED"))
            .andExpect(jsonPath("$.data.events[1].eventType").value("DISPATCHED"));
}
```

**Step 2: Run integration test**

Run: `./gradlew test --tests "TransferControllerIntegrationTest.getTransferHistory_shouldReturnEventHistory" -i`
Expected: PASS

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/TransferControllerIntegrationTest.java
git commit -m "$(cat <<'EOF'
test(transfer): add integration test for history endpoint

Verify history endpoint returns CREATED and DISPATCHED events.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 19: Run Full Test Suite and Final Verification

**Files:** None (verification only)

**Step 1: Run all transfer-related tests**

Run: `./gradlew test --tests "*Transfer*" -i`
Expected: All tests pass

**Step 2: Run full test suite**

Run: `./gradlew test`
Expected: All tests pass

**Step 3: Verify application starts**

Run: `./gradlew bootRun` (stop after startup)
Expected: Application starts without errors

**Step 4: Final commit for Phase 5 completion**

```bash
git add -A
git commit -m "$(cat <<'EOF'
docs(transfer): complete Phase 5 - Operational Robustness

Phase 5 implementation complete:
- Concurrency exception handlers (409 Conflict)
- TransferEvent entity and migration for audit trail
- TransferEventPublisher for lifecycle events
- TransferHistoryService and /history endpoint
- ScanLog entity and migration for scan idempotency
- ScanLogCleanupJob for TTL cleanup
- Integration tests for idempotency and history

All acceptance criteria met per transfer-refactoring-spec.md

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Summary Checklist

| Criterion | Implementation |
|-----------|----------------|
| Two dispatch calls don't duplicate movements | ✅ State-based idempotency in dispatch() |
| Two complete calls don't duplicate entries | ✅ State-based idempotency in completeValidation() |
| All status changes have audit | ✅ TransferEvent + TransferEventPublisher |
| Concurrency handled with locks | ✅ @Version + findByIdForUpdate + exception handlers |
| Transactions are atomic | ✅ @Transactional with REPEATABLE_READ |
| Scan idempotency with key | ✅ ScanLog + idempotencyKey in request |
| History endpoint | ✅ GET /transfers/{id}/history |
