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
