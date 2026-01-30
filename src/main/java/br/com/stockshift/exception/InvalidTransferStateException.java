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
