package br.com.stockshift.model.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

class TransferInTransitTest {

    @Test
    void shouldHaveNullConsumedAtByDefault() {
        TransferInTransit transit = new TransferInTransit();
        assertThat(transit.getConsumedAt()).isNull();
    }

    @Test
    void shouldTrackQuantityInTransit() {
        TransferInTransit transit = new TransferInTransit();
        transit.setQuantity(new BigDecimal("50"));
        assertThat(transit.getQuantity()).isEqualTo(new BigDecimal("50"));
    }

    @Test
    void shouldMarkAsConsumed() {
        TransferInTransit transit = new TransferInTransit();
        transit.setQuantity(new BigDecimal("50"));

        LocalDateTime now = LocalDateTime.now();
        transit.setConsumedAt(now);
        transit.setQuantity(BigDecimal.ZERO);

        assertThat(transit.getConsumedAt()).isEqualTo(now);
        assertThat(transit.getQuantity()).isZero();
    }
}
