package br.com.stockshift.service.transfer;

import br.com.stockshift.dto.transfer.CreateTransferRequest;
import br.com.stockshift.dto.transfer.TransferItemRequest;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.exception.InvalidTransferStateException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.TransferRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.service.WarehouseAccessService;
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

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BatchRepository batchRepository;

    @Captor
    private ArgumentCaptor<Transfer> transferCaptor;

    private TransferService transferService;

    private UUID tenantId;
    private UUID transferId;
    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;
    private Transfer transfer;
    private User user;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
            transferRepository,
            securityService,
            stateMachine,
            warehouseAccessService,
            warehouseRepository,
            productRepository,
            batchRepository
        );

        tenantId = UUID.randomUUID();
        transferId = UUID.randomUUID();
        sourceWarehouseId = UUID.randomUUID();
        destinationWarehouseId = UUID.randomUUID();

        sourceWarehouse = new Warehouse();
        sourceWarehouse.setId(sourceWarehouseId);

        destinationWarehouse = new Warehouse();
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
