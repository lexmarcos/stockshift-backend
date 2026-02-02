package br.com.stockshift.service.transfer;

import br.com.stockshift.model.enums.TransferStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class TransferStateMachine {

    private static final Map<TransferStatus, Set<TransferStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(TransferStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(TransferStatus.DRAFT, Set.of(
                TransferStatus.IN_TRANSIT,
                TransferStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(TransferStatus.IN_TRANSIT, Set.of(
                TransferStatus.PENDING_VALIDATION,
                TransferStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(TransferStatus.PENDING_VALIDATION, Set.of(
                TransferStatus.COMPLETED,
                TransferStatus.COMPLETED_WITH_DISCREPANCY
        ));
        ALLOWED_TRANSITIONS.put(TransferStatus.COMPLETED, Set.of());
        ALLOWED_TRANSITIONS.put(TransferStatus.COMPLETED_WITH_DISCREPANCY, Set.of());
        ALLOWED_TRANSITIONS.put(TransferStatus.CANCELLED, Set.of());
    }

    public boolean canTransition(TransferStatus from, TransferStatus to) {
        Set<TransferStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public void validateTransition(TransferStatus from, TransferStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    String.format("Cannot transition from %s to %s", from, to)
            );
        }
    }
}
