package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TransferActionTest {
    @Test
    void shouldHaveAllRequiredActions() {
        assertThat(TransferAction.values()).containsExactlyInAnyOrder(
            TransferAction.CREATE,
            TransferAction.UPDATE,
            TransferAction.CANCEL,
            TransferAction.DISPATCH,
            TransferAction.START_VALIDATION,
            TransferAction.SCAN_ITEM,
            TransferAction.COMPLETE
        );
    }

    @Test
    void shouldIdentifyOutboundActions() {
        assertThat(TransferAction.CREATE.isOutboundAction()).isTrue();
        assertThat(TransferAction.UPDATE.isOutboundAction()).isTrue();
        assertThat(TransferAction.CANCEL.isOutboundAction()).isTrue();
        assertThat(TransferAction.DISPATCH.isOutboundAction()).isTrue();
        assertThat(TransferAction.START_VALIDATION.isOutboundAction()).isFalse();
        assertThat(TransferAction.SCAN_ITEM.isOutboundAction()).isFalse();
        assertThat(TransferAction.COMPLETE.isOutboundAction()).isFalse();
    }

    @Test
    void shouldIdentifyInboundActions() {
        assertThat(TransferAction.START_VALIDATION.isInboundAction()).isTrue();
        assertThat(TransferAction.SCAN_ITEM.isInboundAction()).isTrue();
        assertThat(TransferAction.COMPLETE.isInboundAction()).isTrue();
        assertThat(TransferAction.CREATE.isInboundAction()).isFalse();
        assertThat(TransferAction.UPDATE.isInboundAction()).isFalse();
        assertThat(TransferAction.CANCEL.isInboundAction()).isFalse();
        assertThat(TransferAction.DISPATCH.isInboundAction()).isFalse();
    }
}
