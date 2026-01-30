# Transfer Refactoring Phase 2 - State Machine & Role-Based Permissions

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the state machine for Transfer status transitions with role-based permission enforcement, ensuring only source warehouse can dispatch and only destination warehouse can validate/receive.

**Architecture:** TransferStateMachine validates transitions based on current status, action, and user role (OUTBOUND/INBOUND). TransferSecurityService determines user role and enforces permissions. InvalidTransferStateException signals invalid transitions. TransferService orchestrates the workflow.

**Tech Stack:** Spring Boot 3, JPA/Hibernate, Spring Security, JUnit 5, AssertJ

---

## Overview

Phase 2 focuses on creating the state machine and security layer for the Transfer system:

1. TransferRole enum (OUTBOUND, INBOUND, NONE)
2. TransferAction enum (CREATE, UPDATE, CANCEL, DISPATCH, START_VALIDATION, SCAN_ITEM, COMPLETE)
3. InvalidTransferStateException for invalid transitions
4. TransferStateMachine with transition validation
5. Database migration for Transfer permissions
6. TransferSecurityService for role determination and permission checks
7. TransferService stub with state machine integration

**Reference Spec:** `.claude/refactoring/transfer-refactoring-spec.md` (Etapa 2)

---

## Task 1: Create TransferRole Enum

**Files:**
- Create: `src/main/java/br/com/stockshift/model/enums/TransferRole.java`
- Test: `src/test/java/br/com/stockshift/model/enums/TransferRoleTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferRoleTest {

    @Test
    void shouldHaveAllRequiredRoles() {
        assertThat(TransferRole.values()).containsExactlyInAnyOrder(
            TransferRole.OUTBOUND,
            TransferRole.INBOUND,
            TransferRole.NONE
        );
    }

    @Test
    void shouldIdentifyOutboundAsSourceRole() {
        assertThat(TransferRole.OUTBOUND.isSourceRole()).isTrue();
        assertThat(TransferRole.INBOUND.isSourceRole()).isFalse();
        assertThat(TransferRole.NONE.isSourceRole()).isFalse();
    }

    @Test
    void shouldIdentifyInboundAsDestinationRole() {
        assertThat(TransferRole.INBOUND.isDestinationRole()).isTrue();
        assertThat(TransferRole.OUTBOUND.isDestinationRole()).isFalse();
        assertThat(TransferRole.NONE.isDestinationRole()).isFalse();
    }

    @Test
    void shouldIdentifyRolesWithAccess() {
        assertThat(TransferRole.OUTBOUND.hasAccess()).isTrue();
        assertThat(TransferRole.INBOUND.hasAccess()).isTrue();
        assertThat(TransferRole.NONE.hasAccess()).isFalse();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferRoleTest -i`
Expected: FAIL with "cannot find symbol: class TransferRole"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.model.enums;

public enum TransferRole {
    OUTBOUND,   // User belongs to source warehouse
    INBOUND,    // User belongs to destination warehouse
    NONE;       // User has no access to either warehouse

    public boolean isSourceRole() {
        return this == OUTBOUND;
    }

    public boolean isDestinationRole() {
        return this == INBOUND;
    }

    public boolean hasAccess() {
        return this != NONE;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferRoleTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/model/enums/TransferRole.java src/test/java/br/com/stockshift/model/enums/TransferRoleTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferRole enum for source/destination role identification

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Create TransferAction Enum

**Files:**
- Create: `src/main/java/br/com/stockshift/model/enums/TransferAction.java`
- Test: `src/test/java/br/com/stockshift/model/enums/TransferActionTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferActionTest {

    @Test
    void shouldHaveAllRequiredActions() {
        assertThat(TransferAction.values()).containsExactlyInAnyOrder(
            TransferAction.CREATE,
            TransferAction.UPDATE,
            TransferAction.CANCEL,
            TransferAction.DISPATCH,
            TransferAction.START_VALIDATION,
            TransferAction.SCAN_ITEM,
            TransferAction.COMPLETE
        );
    }

    @Test
    void shouldIdentifyOutboundActions() {
        assertThat(TransferAction.CREATE.isOutboundAction()).isTrue();
        assertThat(TransferAction.UPDATE.isOutboundAction()).isTrue();
        assertThat(TransferAction.CANCEL.isOutboundAction()).isTrue();
        assertThat(TransferAction.DISPATCH.isOutboundAction()).isTrue();
        assertThat(TransferAction.START_VALIDATION.isOutboundAction()).isFalse();
        assertThat(TransferAction.SCAN_ITEM.isOutboundAction()).isFalse();
        assertThat(TransferAction.COMPLETE.isOutboundAction()).isFalse();
    }

    @Test
    void shouldIdentifyInboundActions() {
        assertThat(TransferAction.START_VALIDATION.isInboundAction()).isTrue();
        assertThat(TransferAction.SCAN_ITEM.isInboundAction()).isTrue();
        assertThat(TransferAction.COMPLETE.isInboundAction()).isTrue();
        assertThat(TransferAction.CREATE.isInboundAction()).isFalse();
        assertThat(TransferAction.UPDATE.isInboundAction()).isFalse();
        assertThat(TransferAction.CANCEL.isInboundAction()).isFalse();
        assertThat(TransferAction.DISPATCH.isInboundAction()).isFalse();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferActionTest -i`
Expected: FAIL with "cannot find symbol: class TransferAction"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.model.enums;

public enum TransferAction {
    CREATE(true, false),
    UPDATE(true, false),
    CANCEL(true, false),
    DISPATCH(true, false),
    START_VALIDATION(false, true),
    SCAN_ITEM(false, true),
    COMPLETE(false, true);

    private final boolean outboundAction;
    private final boolean inboundAction;

    TransferAction(boolean outboundAction, boolean inboundAction) {
        this.outboundAction = outboundAction;
        this.inboundAction = inboundAction;
    }

    public boolean isOutboundAction() {
        return outboundAction;
    }

    public boolean isInboundAction() {
        return inboundAction;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferActionTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/model/enums/TransferAction.java src/test/java/br/com/stockshift/model/enums/TransferActionTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferAction enum with outbound/inbound classification

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Create InvalidTransferStateException

**Files:**
- Create: `src/main/java/br/com/stockshift/exception/InvalidTransferStateException.java`
- Test: `src/test/java/br/com/stockshift/exception/InvalidTransferStateExceptionTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.exception;

import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.model.enums.TransferStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidTransferStateExceptionTest {

    @Test
    void shouldCreateExceptionWithMessage() {
        InvalidTransferStateException exception = new InvalidTransferStateException(
            "Cannot dispatch transfer"
        );

        assertThat(exception.getMessage()).isEqualTo("Cannot dispatch transfer");
    }

    @Test
    void shouldCreateExceptionWithStatusActionAndRole() {
        InvalidTransferStateException exception = new InvalidTransferStateException(
            TransferStatus.IN_TRANSIT,
            TransferAction.DISPATCH,
            TransferRole.OUTBOUND
        );

        assertThat(exception.getMessage()).contains("IN_TRANSIT");
        assertThat(exception.getMessage()).contains("DISPATCH");
        assertThat(exception.getMessage()).contains("OUTBOUND");
        assertThat(exception.getCurrentStatus()).isEqualTo(TransferStatus.IN_TRANSIT);
        assertThat(exception.getAttemptedAction()).isEqualTo(TransferAction.DISPATCH);
        assertThat(exception.getUserRole()).isEqualTo(TransferRole.OUTBOUND);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests InvalidTransferStateExceptionTest -i`
Expected: FAIL with "cannot find symbol: class InvalidTransferStateException"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.exception;

import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.model.enums.TransferStatus;
import lombok.Getter;

@Getter
public class InvalidTransferStateException extends BusinessException {

    private final TransferStatus currentStatus;
    private final TransferAction attemptedAction;
    private final TransferRole userRole;

    public InvalidTransferStateException(String message) {
        super(message);
        this.currentStatus = null;
        this.attemptedAction = null;
        this.userRole = null;
    }

    public InvalidTransferStateException(
            TransferStatus currentStatus,
            TransferAction attemptedAction,
            TransferRole userRole
    ) {
        super(String.format(
            "Cannot %s transfer in status %s with role %s",
            attemptedAction, currentStatus, userRole
        ));
        this.currentStatus = currentStatus;
        this.attemptedAction = attemptedAction;
        this.userRole = userRole;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests InvalidTransferStateExceptionTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/exception/InvalidTransferStateException.java src/test/java/br/com/stockshift/exception/InvalidTransferStateExceptionTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add InvalidTransferStateException for state machine violations

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Create TransferStateMachine

**Files:**
- Create: `src/main/java/br/com/stockshift/service/transfer/TransferStateMachine.java`
- Test: `src/test/java/br/com/stockshift/service/transfer/TransferStateMachineTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.exception.InvalidTransferStateException;
import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.model.enums.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferStateMachineTest {

    private TransferStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new TransferStateMachine();
    }

    @Nested
    class OutboundActions {

        @Test
        void shouldAllowUpdateInDraftByOutbound() {
            assertThat(stateMachine.canTransition(
                TransferStatus.DRAFT, TransferAction.UPDATE, TransferRole.OUTBOUND
            )).isTrue();

            TransferStatus result = stateMachine.transition(
                TransferStatus.DRAFT, TransferAction.UPDATE, TransferRole.OUTBOUND
            );
            assertThat(result).isEqualTo(TransferStatus.DRAFT);
        }

        @Test
        void shouldAllowCancelInDraftByOutbound() {
            assertThat(stateMachine.canTransition(
                TransferStatus.DRAFT, TransferAction.CANCEL, TransferRole.OUTBOUND
            )).isTrue();

            TransferStatus result = stateMachine.transition(
                TransferStatus.DRAFT, TransferAction.CANCEL, TransferRole.OUTBOUND
            );
            assertThat(result).isEqualTo(TransferStatus.CANCELLED);
        }

        @Test
        void shouldAllowDispatchInDraftByOutbound() {
            assertThat(stateMachine.canTransition(
                TransferStatus.DRAFT, TransferAction.DISPATCH, TransferRole.OUTBOUND
            )).isTrue();

            TransferStatus result = stateMachine.transition(
                TransferStatus.DRAFT, TransferAction.DISPATCH, TransferRole.OUTBOUND
            );
            assertThat(result).isEqualTo(TransferStatus.IN_TRANSIT);
        }

        @Test
        void shouldRejectDispatchByInbound() {
            assertThat(stateMachine.canTransition(
                TransferStatus.DRAFT, TransferAction.DISPATCH, TransferRole.INBOUND
            )).isFalse();

            assertThatThrownBy(() -> stateMachine.transition(
                TransferStatus.DRAFT, TransferAction.DISPATCH, TransferRole.INBOUND
            )).isInstanceOf(InvalidTransferStateException.class);
        }

        @Test
        void shouldRejectCancelInTransit() {
            assertThat(stateMachine.canTransition(
                TransferStatus.IN_TRANSIT, TransferAction.CANCEL, TransferRole.OUTBOUND
            )).isFalse();
        }
    }

    @Nested
    class InboundActions {

        @Test
        void shouldAllowStartValidationInTransitByInbound() {
            assertThat(stateMachine.canTransition(
                TransferStatus.IN_TRANSIT, TransferAction.START_VALIDATION, TransferRole.INBOUND
            )).isTrue();

            TransferStatus result = stateMachine.transition(
                TransferStatus.IN_TRANSIT, TransferAction.START_VALIDATION, TransferRole.INBOUND
            );
            assertThat(result).isEqualTo(TransferStatus.VALIDATION_IN_PROGRESS);
        }

        @Test
        void shouldAllowScanItemDuringValidationByInbound() {
            assertThat(stateMachine.canTransition(
                TransferStatus.VALIDATION_IN_PROGRESS, TransferAction.SCAN_ITEM, TransferRole.INBOUND
            )).isTrue();

            TransferStatus result = stateMachine.transition(
                TransferStatus.VALIDATION_IN_PROGRESS, TransferAction.SCAN_ITEM, TransferRole.INBOUND
            );
            assertThat(result).isEqualTo(TransferStatus.VALIDATION_IN_PROGRESS);
        }

        @Test
        void shouldAllowCompleteValidationByInbound() {
            assertThat(stateMachine.canTransition(
                TransferStatus.VALIDATION_IN_PROGRESS, TransferAction.COMPLETE, TransferRole.INBOUND
            )).isTrue();

            // COMPLETE returns COMPLETED - actual status may be COMPLETED_WITH_DISCREPANCY
            // based on business logic, but state machine returns base success status
            TransferStatus result = stateMachine.transition(
                TransferStatus.VALIDATION_IN_PROGRESS, TransferAction.COMPLETE, TransferRole.INBOUND
            );
            assertThat(result).isEqualTo(TransferStatus.COMPLETED);
        }

        @Test
        void shouldRejectStartValidationByOutbound() {
            assertThat(stateMachine.canTransition(
                TransferStatus.IN_TRANSIT, TransferAction.START_VALIDATION, TransferRole.OUTBOUND
            )).isFalse();

            assertThatThrownBy(() -> stateMachine.transition(
                TransferStatus.IN_TRANSIT, TransferAction.START_VALIDATION, TransferRole.OUTBOUND
            )).isInstanceOf(InvalidTransferStateException.class);
        }
    }

    @Nested
    class TerminalStates {

        @Test
        void shouldRejectAllActionsOnCompletedTransfer() {
            for (TransferAction action : TransferAction.values()) {
                assertThat(stateMachine.canTransition(
                    TransferStatus.COMPLETED, action, TransferRole.OUTBOUND
                )).isFalse();
                assertThat(stateMachine.canTransition(
                    TransferStatus.COMPLETED, action, TransferRole.INBOUND
                )).isFalse();
            }
        }

        @Test
        void shouldRejectAllActionsOnCancelledTransfer() {
            for (TransferAction action : TransferAction.values()) {
                assertThat(stateMachine.canTransition(
                    TransferStatus.CANCELLED, action, TransferRole.OUTBOUND
                )).isFalse();
            }
        }
    }

    @Nested
    class NoAccessRole {

        @Test
        void shouldRejectAllActionsForNoneRole() {
            for (TransferAction action : TransferAction.values()) {
                for (TransferStatus status : TransferStatus.values()) {
                    assertThat(stateMachine.canTransition(
                        status, action, TransferRole.NONE
                    )).isFalse();
                }
            }
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferStateMachineTest -i`
Expected: FAIL with "cannot find symbol: class TransferStateMachine"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.exception.InvalidTransferStateException;
import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.model.enums.TransferStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TransferStateMachine {

    private record TransitionKey(TransferStatus status, TransferAction action, TransferRole role) {}

    private static final Map<TransitionKey, TransferStatus> ALLOWED_TRANSITIONS = Map.ofEntries(
        // OUTBOUND actions from DRAFT
        Map.entry(
            new TransitionKey(TransferStatus.DRAFT, TransferAction.UPDATE, TransferRole.OUTBOUND),
            TransferStatus.DRAFT
        ),
        Map.entry(
            new TransitionKey(TransferStatus.DRAFT, TransferAction.CANCEL, TransferRole.OUTBOUND),
            TransferStatus.CANCELLED
        ),
        Map.entry(
            new TransitionKey(TransferStatus.DRAFT, TransferAction.DISPATCH, TransferRole.OUTBOUND),
            TransferStatus.IN_TRANSIT
        ),

        // INBOUND actions from IN_TRANSIT
        Map.entry(
            new TransitionKey(TransferStatus.IN_TRANSIT, TransferAction.START_VALIDATION, TransferRole.INBOUND),
            TransferStatus.VALIDATION_IN_PROGRESS
        ),

        // INBOUND actions from VALIDATION_IN_PROGRESS
        Map.entry(
            new TransitionKey(TransferStatus.VALIDATION_IN_PROGRESS, TransferAction.SCAN_ITEM, TransferRole.INBOUND),
            TransferStatus.VALIDATION_IN_PROGRESS
        ),
        Map.entry(
            new TransitionKey(TransferStatus.VALIDATION_IN_PROGRESS, TransferAction.COMPLETE, TransferRole.INBOUND),
            TransferStatus.COMPLETED  // Business logic determines if COMPLETED_WITH_DISCREPANCY
        )
    );

    public boolean canTransition(
            TransferStatus currentStatus,
            TransferAction action,
            TransferRole role
    ) {
        if (role == TransferRole.NONE) {
            return false;
        }
        if (currentStatus.isTerminal()) {
            return false;
        }
        return ALLOWED_TRANSITIONS.containsKey(new TransitionKey(currentStatus, action, role));
    }

    public TransferStatus transition(
            TransferStatus currentStatus,
            TransferAction action,
            TransferRole role
    ) {
        if (!canTransition(currentStatus, action, role)) {
            throw new InvalidTransferStateException(currentStatus, action, role);
        }
        return ALLOWED_TRANSITIONS.get(new TransitionKey(currentStatus, action, role));
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferStateMachineTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferStateMachine.java src/test/java/br/com/stockshift/service/transfer/TransferStateMachineTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferStateMachine with role-based transition validation

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Create Database Migration for Transfer Permissions

**Files:**
- Create: `src/main/resources/db/migration/V30__add_transfer_permissions.sql`

**Step 1: Write the migration file**

```sql
-- Add Transfer-specific permissions
INSERT INTO permissions (id, name, description, resource, action, scope, created_at, updated_at)
SELECT
    uuid_generate_v4(),
    'TRANSFER_CREATE',
    'Create transfers from source warehouse',
    'TRANSFER',
    'CREATE',
    'TENANT',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'TRANSFER_CREATE');

INSERT INTO permissions (id, name, description, resource, action, scope, created_at, updated_at)
SELECT
    uuid_generate_v4(),
    'TRANSFER_UPDATE',
    'Update transfers in DRAFT status',
    'TRANSFER',
    'UPDATE',
    'TENANT',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'TRANSFER_UPDATE');

INSERT INTO permissions (id, name, description, resource, action, scope, created_at, updated_at)
SELECT
    uuid_generate_v4(),
    'TRANSFER_DELETE',
    'Cancel transfers in DRAFT status',
    'TRANSFER',
    'DELETE',
    'TENANT',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'TRANSFER_DELETE');

INSERT INTO permissions (id, name, description, resource, action, scope, created_at, updated_at)
SELECT
    uuid_generate_v4(),
    'TRANSFER_EXECUTE',
    'Dispatch transfers from source warehouse',
    'TRANSFER',
    'EXECUTE',
    'TENANT',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'TRANSFER_EXECUTE');

INSERT INTO permissions (id, name, description, resource, action, scope, created_at, updated_at)
SELECT
    uuid_generate_v4(),
    'TRANSFER_VALIDATE',
    'Validate and receive transfers at destination warehouse',
    'TRANSFER',
    'VALIDATE',
    'TENANT',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'TRANSFER_VALIDATE');

INSERT INTO permissions (id, name, description, resource, action, scope, created_at, updated_at)
SELECT
    uuid_generate_v4(),
    'TRANSFER_VIEW',
    'View transfers',
    'TRANSFER',
    'READ',
    'TENANT',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'TRANSFER_VIEW');

INSERT INTO permissions (id, name, description, resource, action, scope, created_at, updated_at)
SELECT
    uuid_generate_v4(),
    'TRANSFER_RESOLVE_DISCREPANCY',
    'Resolve transfer discrepancies',
    'TRANSFER',
    'RESOLVE',
    'TENANT',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'TRANSFER_RESOLVE_DISCREPANCY');
```

**Step 2: Run migration to verify it works**

Run: `./gradlew test --tests BaseIntegrationTest -i`
Expected: PASS (migrations apply successfully)

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V30__add_transfer_permissions.sql
git commit -m "$(cat <<'EOF'
feat(transfer): add database migration for transfer-specific permissions

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Create TransferSecurityService

**Files:**
- Create: `src/main/java/br/com/stockshift/service/transfer/TransferSecurityService.java`
- Test: `src/test/java/br/com/stockshift/service/transfer/TransferSecurityServiceTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.service.WarehouseAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferSecurityServiceTest {

    @Mock
    private WarehouseAccessService warehouseAccessService;

    @Mock
    private TransferStateMachine stateMachine;

    private TransferSecurityService securityService;

    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;
    private Transfer transfer;

    @BeforeEach
    void setUp() {
        securityService = new TransferSecurityService(warehouseAccessService, stateMachine);

        sourceWarehouseId = UUID.randomUUID();
        destinationWarehouseId = UUID.randomUUID();

        Warehouse sourceWarehouse = new Warehouse();
        sourceWarehouse.setId(sourceWarehouseId);

        Warehouse destinationWarehouse = new Warehouse();
        destinationWarehouse.setId(destinationWarehouseId);

        transfer = new Transfer();
        transfer.setSourceWarehouse(sourceWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);
        transfer.setStatus(TransferStatus.DRAFT);
    }

    @Nested
    class DetermineUserRole {

        @Test
        void shouldReturnOutboundWhenUserHasSourceWarehouseAccess() {
            when(warehouseAccessService.getUserWarehouseIds())
                .thenReturn(Set.of(sourceWarehouseId));

            TransferRole role = securityService.determineUserRole(transfer);

            assertThat(role).isEqualTo(TransferRole.OUTBOUND);
        }

        @Test
        void shouldReturnInboundWhenUserHasDestinationWarehouseAccess() {
            when(warehouseAccessService.getUserWarehouseIds())
                .thenReturn(Set.of(destinationWarehouseId));

            TransferRole role = securityService.determineUserRole(transfer);

            assertThat(role).isEqualTo(TransferRole.INBOUND);
        }

        @Test
        void shouldReturnOutboundWhenUserHasBothWarehouseAccess() {
            // OUTBOUND takes priority per spec
            when(warehouseAccessService.getUserWarehouseIds())
                .thenReturn(Set.of(sourceWarehouseId, destinationWarehouseId));

            TransferRole role = securityService.determineUserRole(transfer);

            assertThat(role).isEqualTo(TransferRole.OUTBOUND);
        }

        @Test
        void shouldReturnNoneWhenUserHasNoWarehouseAccess() {
            when(warehouseAccessService.getUserWarehouseIds())
                .thenReturn(Set.of(UUID.randomUUID()));

            TransferRole role = securityService.determineUserRole(transfer);

            assertThat(role).isEqualTo(TransferRole.NONE);
        }

        @Test
        void shouldReturnOutboundForFullAccessUser() {
            when(warehouseAccessService.hasFullAccess()).thenReturn(true);
            when(warehouseAccessService.getUserWarehouseIds()).thenReturn(Set.of());

            TransferRole role = securityService.determineUserRole(transfer);

            assertThat(role).isEqualTo(TransferRole.OUTBOUND);
        }
    }

    @Nested
    class ValidateAction {

        @Test
        void shouldAllowValidTransition() {
            when(warehouseAccessService.getUserWarehouseIds())
                .thenReturn(Set.of(sourceWarehouseId));
            when(stateMachine.canTransition(TransferStatus.DRAFT, TransferAction.DISPATCH, TransferRole.OUTBOUND))
                .thenReturn(true);

            // Should not throw
            securityService.validateAction(transfer, TransferAction.DISPATCH);
        }

        @Test
        void shouldThrowForbiddenForInvalidTransition() {
            when(warehouseAccessService.getUserWarehouseIds())
                .thenReturn(Set.of(destinationWarehouseId));
            when(stateMachine.canTransition(TransferStatus.DRAFT, TransferAction.DISPATCH, TransferRole.INBOUND))
                .thenReturn(false);

            assertThatThrownBy(() -> securityService.validateAction(transfer, TransferAction.DISPATCH))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("cannot perform");
        }

        @Test
        void shouldThrowForbiddenForNoAccess() {
            when(warehouseAccessService.getUserWarehouseIds())
                .thenReturn(Set.of(UUID.randomUUID()));

            assertThatThrownBy(() -> securityService.validateAction(transfer, TransferAction.DISPATCH))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no access");
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferSecurityServiceTest -i`
Expected: FAIL with "cannot find symbol: class TransferSecurityService"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.service.WarehouseAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferSecurityService {

    private final WarehouseAccessService warehouseAccessService;
    private final TransferStateMachine stateMachine;

    public TransferRole determineUserRole(Transfer transfer) {
        // Full access users are treated as OUTBOUND (can do everything)
        if (warehouseAccessService.hasFullAccess()) {
            return TransferRole.OUTBOUND;
        }

        Set<UUID> userWarehouseIds = warehouseAccessService.getUserWarehouseIds();
        UUID sourceId = transfer.getSourceWarehouse().getId();
        UUID destinationId = transfer.getDestinationWarehouse().getId();

        // OUTBOUND takes priority if user has access to both
        if (userWarehouseIds.contains(sourceId)) {
            return TransferRole.OUTBOUND;
        }
        if (userWarehouseIds.contains(destinationId)) {
            return TransferRole.INBOUND;
        }

        return TransferRole.NONE;
    }

    public void validateAction(Transfer transfer, TransferAction action) {
        TransferRole role = determineUserRole(transfer);

        if (!role.hasAccess()) {
            log.warn("User has no access to transfer {}. Source: {}, Destination: {}",
                transfer.getId(),
                transfer.getSourceWarehouse().getId(),
                transfer.getDestinationWarehouse().getId()
            );
            throw new ForbiddenException("You have no access to this transfer");
        }

        if (!stateMachine.canTransition(transfer.getStatus(), action, role)) {
            log.warn("User with role {} cannot perform {} on transfer {} in status {}",
                role, action, transfer.getId(), transfer.getStatus()
            );
            throw new ForbiddenException(
                String.format("User with role %s cannot perform %s on transfer in status %s",
                    role, action, transfer.getStatus())
            );
        }
    }

    public void validateSourceWarehouseAccess(UUID sourceWarehouseId) {
        if (warehouseAccessService.hasFullAccess()) {
            return;
        }

        Set<UUID> userWarehouseIds = warehouseAccessService.getUserWarehouseIds();
        if (!userWarehouseIds.contains(sourceWarehouseId)) {
            throw new ForbiddenException("You don't have access to the source warehouse");
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferSecurityServiceTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferSecurityService.java src/test/java/br/com/stockshift/service/transfer/TransferSecurityServiceTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferSecurityService for role determination and action validation

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Create TransferService Stub with State Machine Integration

**Files:**
- Create: `src/main/java/br/com/stockshift/service/transfer/TransferService.java`
- Test: `src/test/java/br/com/stockshift/service/transfer/TransferServiceTest.java`

**Step 1: Write the failing test**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.exception.InvalidTransferStateException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.TransferRepository;
import br.com.stockshift.service.WarehouseAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private TransferSecurityService securityService;

    @Mock
    private TransferStateMachine stateMachine;

    @Mock
    private WarehouseAccessService warehouseAccessService;

    @Captor
    private ArgumentCaptor<Transfer> transferCaptor;

    private TransferService transferService;

    private UUID tenantId;
    private UUID transferId;
    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;
    private Transfer transfer;
    private User user;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
            transferRepository,
            securityService,
            stateMachine,
            warehouseAccessService
        );

        tenantId = UUID.randomUUID();
        transferId = UUID.randomUUID();
        sourceWarehouseId = UUID.randomUUID();
        destinationWarehouseId = UUID.randomUUID();

        Warehouse sourceWarehouse = new Warehouse();
        sourceWarehouse.setId(sourceWarehouseId);

        Warehouse destinationWarehouse = new Warehouse();
        destinationWarehouse.setId(destinationWarehouseId);

        transfer = new Transfer();
        transfer.setId(transferId);
        transfer.setTenantId(tenantId);
        transfer.setSourceWarehouse(sourceWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);
        transfer.setStatus(TransferStatus.DRAFT);
        transfer.setTransferCode("TRF-2026-00001");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
    }

    @Nested
    class Dispatch {

        @Test
        void shouldDispatchTransferSuccessfully() {
            when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
            when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
            doNothing().when(securityService).validateAction(any(), any());
            when(stateMachine.transition(TransferStatus.DRAFT, TransferAction.DISPATCH, TransferRole.OUTBOUND))
                .thenReturn(TransferStatus.IN_TRANSIT);
            when(securityService.determineUserRole(transfer)).thenReturn(TransferRole.OUTBOUND);
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Transfer result = transferService.dispatch(transferId, user);

            assertThat(result.getStatus()).isEqualTo(TransferStatus.IN_TRANSIT);
            assertThat(result.getDispatchedBy()).isEqualTo(user);
            assertThat(result.getDispatchedAt()).isNotNull();

            verify(transferRepository).save(transferCaptor.capture());
            Transfer saved = transferCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(TransferStatus.IN_TRANSIT);
        }

        @Test
        void shouldBeIdempotentIfAlreadyDispatched() {
            transfer.setStatus(TransferStatus.IN_TRANSIT);
            when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
            when(warehouseAccessService.getTenantId()).thenReturn(tenantId);

            Transfer result = transferService.dispatch(transferId, user);

            assertThat(result.getStatus()).isEqualTo(TransferStatus.IN_TRANSIT);
            verify(transferRepository, never()).save(any());
        }

        @Test
        void shouldThrowNotFoundForInvalidTransferId() {
            when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transferService.dispatch(transferId, user))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void shouldThrowForbiddenForWrongTenant() {
            when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
            when(warehouseAccessService.getTenantId()).thenReturn(UUID.randomUUID());

            assertThatThrownBy(() -> transferService.dispatch(transferId, user))
                .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class StartValidation {

        @BeforeEach
        void setUp() {
            transfer.setStatus(TransferStatus.IN_TRANSIT);
        }

        @Test
        void shouldStartValidationSuccessfully() {
            when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
            when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
            doNothing().when(securityService).validateAction(any(), any());
            when(stateMachine.transition(TransferStatus.IN_TRANSIT, TransferAction.START_VALIDATION, TransferRole.INBOUND))
                .thenReturn(TransferStatus.VALIDATION_IN_PROGRESS);
            when(securityService.determineUserRole(transfer)).thenReturn(TransferRole.INBOUND);
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Transfer result = transferService.startValidation(transferId, user);

            assertThat(result.getStatus()).isEqualTo(TransferStatus.VALIDATION_IN_PROGRESS);
            assertThat(result.getValidationStartedBy()).isEqualTo(user);
            assertThat(result.getValidationStartedAt()).isNotNull();
        }

        @Test
        void shouldBeIdempotentIfValidationAlreadyStarted() {
            transfer.setStatus(TransferStatus.VALIDATION_IN_PROGRESS);
            when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
            when(warehouseAccessService.getTenantId()).thenReturn(tenantId);

            Transfer result = transferService.startValidation(transferId, user);

            assertThat(result.getStatus()).isEqualTo(TransferStatus.VALIDATION_IN_PROGRESS);
            verify(transferRepository, never()).save(any());
        }
    }

    @Nested
    class Cancel {

        @Test
        void shouldCancelTransferSuccessfully() {
            when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
            when(warehouseAccessService.getTenantId()).thenReturn(tenantId);
            doNothing().when(securityService).validateAction(any(), any());
            when(stateMachine.transition(TransferStatus.DRAFT, TransferAction.CANCEL, TransferRole.OUTBOUND))
                .thenReturn(TransferStatus.CANCELLED);
            when(securityService.determineUserRole(transfer)).thenReturn(TransferRole.OUTBOUND);
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Transfer result = transferService.cancel(transferId, "Test cancellation", user);

            assertThat(result.getStatus()).isEqualTo(TransferStatus.CANCELLED);
            assertThat(result.getCancelledBy()).isEqualTo(user);
            assertThat(result.getCancelledAt()).isNotNull();
            assertThat(result.getCancellationReason()).isEqualTo("Test cancellation");
        }

        @Test
        void shouldBeIdempotentIfAlreadyCancelled() {
            transfer.setStatus(TransferStatus.CANCELLED);
            when(transferRepository.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
            when(warehouseAccessService.getTenantId()).thenReturn(tenantId);

            Transfer result = transferService.cancel(transferId, "reason", user);

            assertThat(result.getStatus()).isEqualTo(TransferStatus.CANCELLED);
            verify(transferRepository, never()).save(any());
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests TransferServiceTest -i`
Expected: FAIL with "cannot find symbol: class TransferService"

**Step 3: Write minimal implementation**

```java
package br.com.stockshift.service.transfer;

import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.TransferRepository;
import br.com.stockshift.service.WarehouseAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TransferRepository transferRepository;
    private final TransferSecurityService securityService;
    private final TransferStateMachine stateMachine;
    private final WarehouseAccessService warehouseAccessService;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Transfer dispatch(UUID transferId, User user) {
        log.info("Dispatching transfer {} by user {}", transferId, user.getId());

        Transfer transfer = getTransferForUpdate(transferId);

        // Idempotency: if already dispatched, return success
        if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            log.info("Transfer {} already dispatched, returning existing state", transferId);
            return transfer;
        }

        // Validate action
        securityService.validateAction(transfer, TransferAction.DISPATCH);

        // Execute state transition
        TransferRole role = securityService.determineUserRole(transfer);
        TransferStatus newStatus = stateMachine.transition(transfer.getStatus(), TransferAction.DISPATCH, role);

        // Update transfer
        transfer.setStatus(newStatus);
        transfer.setDispatchedBy(user);
        transfer.setDispatchedAt(LocalDateTime.now());

        transfer = transferRepository.save(transfer);
        log.info("Transfer {} dispatched successfully", transferId);

        return transfer;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Transfer startValidation(UUID transferId, User user) {
        log.info("Starting validation for transfer {} by user {}", transferId, user.getId());

        Transfer transfer = getTransferForUpdate(transferId);

        // Idempotency: if already in validation, return success
        if (transfer.getStatus() == TransferStatus.VALIDATION_IN_PROGRESS) {
            log.info("Transfer {} validation already started, returning existing state", transferId);
            return transfer;
        }

        // Validate action
        securityService.validateAction(transfer, TransferAction.START_VALIDATION);

        // Execute state transition
        TransferRole role = securityService.determineUserRole(transfer);
        TransferStatus newStatus = stateMachine.transition(transfer.getStatus(), TransferAction.START_VALIDATION, role);

        // Update transfer
        transfer.setStatus(newStatus);
        transfer.setValidationStartedBy(user);
        transfer.setValidationStartedAt(LocalDateTime.now());

        transfer = transferRepository.save(transfer);
        log.info("Transfer {} validation started successfully", transferId);

        return transfer;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Transfer cancel(UUID transferId, String reason, User user) {
        log.info("Cancelling transfer {} by user {}", transferId, user.getId());

        Transfer transfer = getTransferForUpdate(transferId);

        // Idempotency: if already cancelled, return success
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            log.info("Transfer {} already cancelled, returning existing state", transferId);
            return transfer;
        }

        // Validate action
        securityService.validateAction(transfer, TransferAction.CANCEL);

        // Execute state transition
        TransferRole role = securityService.determineUserRole(transfer);
        TransferStatus newStatus = stateMachine.transition(transfer.getStatus(), TransferAction.CANCEL, role);

        // Update transfer
        transfer.setStatus(newStatus);
        transfer.setCancelledBy(user);
        transfer.setCancelledAt(LocalDateTime.now());
        transfer.setCancellationReason(reason);

        transfer = transferRepository.save(transfer);
        log.info("Transfer {} cancelled successfully", transferId);

        return transfer;
    }

    private Transfer getTransferForUpdate(UUID transferId) {
        Transfer transfer = transferRepository.findByIdForUpdate(transferId)
            .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + transferId));

        // Validate tenant access
        UUID currentTenantId = warehouseAccessService.getTenantId();
        if (!transfer.getTenantId().equals(currentTenantId)) {
            throw new ForbiddenException("Transfer not found");
        }

        return transfer;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests TransferServiceTest -i`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/transfer/TransferService.java src/test/java/br/com/stockshift/service/transfer/TransferServiceTest.java
git commit -m "$(cat <<'EOF'
feat(transfer): add TransferService with state machine integration and idempotency

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Run Full Test Suite

**Step 1: Run all tests to ensure nothing is broken**

Run: `./gradlew test -i`
Expected: All tests PASS

**Step 2: Verify migrations apply correctly**

The integration tests use Testcontainers which will apply all migrations. If tests pass, migrations are correct.

**Step 3: Commit any fixes if needed**

If any tests fail, fix them before proceeding.

---

## Task 9: Final Verification

**Step 1: Verify all new files are committed**

Run: `git status`
Expected: Clean working directory

**Step 2: Review the commit log**

Run: `git log --oneline -10`
Expected: See all commits from this phase

**Step 3: Tag the phase completion**

```bash
git tag -a v0.2.0-transfer-phase2 -m "Transfer Refactoring Phase 2: State Machine & Role-Based Permissions complete"
```

---

## Summary

Phase 2 creates the state machine and security layer for the Transfer system:

| Component | Files Created |
|-----------|---------------|
| Enums | `TransferRole`, `TransferAction` |
| Exceptions | `InvalidTransferStateException` |
| Services | `TransferStateMachine`, `TransferSecurityService`, `TransferService` |
| Migrations | `V30` (Transfer permissions) |

**Invariants Enforced:**
- I1: Only source warehouse can dispatch (TransferSecurityService + OUTBOUND role)
- I2: Only destination warehouse can validate/receive (TransferSecurityService + INBOUND role)
- I4: Transfer can only be dispatched if status = DRAFT (TransferStateMachine)
- I5: Transfer can only be validated if status = IN_TRANSIT (TransferStateMachine)

**Key Patterns:**
- State machine pattern for transition validation
- Role-based access control (OUTBOUND/INBOUND/NONE)
- Idempotency for all state-changing operations
- Pessimistic locking for concurrent access

**Next Phase:** Phase 3 will implement the full API endpoints and ledger integration.
