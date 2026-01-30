package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.TransferItemStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferItemTest {

    @Test
    void shouldCreateItemWithPendingStatus() {
        TransferItem item = new TransferItem();
        assertThat(item.getStatus()).isEqualTo(TransferItemStatus.PENDING);
    }

    @Test
    void shouldHaveNullReceivedQuantityByDefault() {
        TransferItem item = new TransferItem();
        assertThat(item.getReceivedQuantity()).isNull();
    }

    @Test
    void shouldHaveNullDestinationBatchByDefault() {
        TransferItem item = new TransferItem();
        assertThat(item.getDestinationBatch()).isNull();
    }
}
