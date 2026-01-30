package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferStatusTest {

    @Test
    void shouldHaveAllRequiredStatuses() {
        assertThat(TransferStatus.values()).containsExactlyInAnyOrder(
            TransferStatus.DRAFT,
            TransferStatus.IN_TRANSIT,
            TransferStatus.VALIDATION_IN_PROGRESS,
            TransferStatus.COMPLETED,
            TransferStatus.COMPLETED_WITH_DISCREPANCY,
            TransferStatus.CANCELLED
        );
    }

    @Test
    void shouldIdentifyTerminalStatuses() {
        assertThat(TransferStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(TransferStatus.COMPLETED_WITH_DISCREPANCY.isTerminal()).isTrue();
        assertThat(TransferStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(TransferStatus.DRAFT.isTerminal()).isFalse();
        assertThat(TransferStatus.IN_TRANSIT.isTerminal()).isFalse();
        assertThat(TransferStatus.VALIDATION_IN_PROGRESS.isTerminal()).isFalse();
    }
}
