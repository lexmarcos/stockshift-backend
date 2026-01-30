package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferItemStatusTest {

    @Test
    void shouldHaveAllRequiredStatuses() {
        assertThat(TransferItemStatus.values()).containsExactlyInAnyOrder(
            TransferItemStatus.PENDING,
            TransferItemStatus.RECEIVED,
            TransferItemStatus.PARTIAL,
            TransferItemStatus.EXCESS,
            TransferItemStatus.MISSING
        );
    }
}
