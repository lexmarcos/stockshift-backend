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
