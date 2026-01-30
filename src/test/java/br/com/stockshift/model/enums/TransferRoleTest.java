package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TransferRoleTest {
    @Test
    void shouldHaveAllRequiredRoles() {
        assertThat(TransferRole.values()).containsExactlyInAnyOrder(
            TransferRole.OUTBOUND,
            TransferRole.INBOUND,
            TransferRole.NONE
        );
    }

    @Test
    void shouldIdentifyOutboundAsSourceRole() {
        assertThat(TransferRole.OUTBOUND.isSourceRole()).isTrue();
        assertThat(TransferRole.INBOUND.isSourceRole()).isFalse();
        assertThat(TransferRole.NONE.isSourceRole()).isFalse();
    }

    @Test
    void shouldIdentifyInboundAsDestinationRole() {
        assertThat(TransferRole.INBOUND.isDestinationRole()).isTrue();
        assertThat(TransferRole.OUTBOUND.isDestinationRole()).isFalse();
        assertThat(TransferRole.NONE.isDestinationRole()).isFalse();
    }

    @Test
    void shouldIdentifyRolesWithAccess() {
        assertThat(TransferRole.OUTBOUND.hasAccess()).isTrue();
        assertThat(TransferRole.INBOUND.hasAccess()).isTrue();
        assertThat(TransferRole.NONE.hasAccess()).isFalse();
    }
}
