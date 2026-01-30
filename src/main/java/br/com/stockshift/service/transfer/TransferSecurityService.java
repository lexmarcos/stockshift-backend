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
