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
