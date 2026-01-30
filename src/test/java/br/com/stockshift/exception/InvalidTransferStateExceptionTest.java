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
