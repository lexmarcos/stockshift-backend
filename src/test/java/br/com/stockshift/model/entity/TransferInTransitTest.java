package br.com.stockshift.model.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TransferInTransitTest {

    @Test
    void shouldHaveNullConsumedAtByDefault() {
        TransferInTransit transit = new TransferInTransit();
        assertThat(transit.getConsumedAt()).isNull();
    }

    @Test
    void shouldTrackQuantityInTransit() {
        TransferInTransit transit = new TransferInTransit();
        transit.setQuantity(50);
        assertThat(transit.getQuantity()).isEqualTo(50);
    }

    @Test
    void shouldMarkAsConsumed() {
        TransferInTransit transit = new TransferInTransit();
        transit.setQuantity(50);

        LocalDateTime now = LocalDateTime.now();
        transit.setConsumedAt(now);
        transit.setQuantity(0);

        assertThat(transit.getConsumedAt()).isEqualTo(now);
        assertThat(transit.getQuantity()).isZero();
    }
}
