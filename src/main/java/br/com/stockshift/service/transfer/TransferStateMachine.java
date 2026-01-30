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
