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
