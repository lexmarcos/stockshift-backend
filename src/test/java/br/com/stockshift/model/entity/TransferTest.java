package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.TransferStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferTest {

    @Test
    void shouldCreateTransferWithDraftStatus() {
        Transfer transfer = new Transfer();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.DRAFT);
    }

    @Test
    void shouldInitializeEmptyItemsList() {
        Transfer transfer = new Transfer();
        assertThat(transfer.getItems()).isNotNull().isEmpty();
    }

    @Test
    void shouldAddItemToTransfer() {
        Transfer transfer = new Transfer();
        TransferItem item = new TransferItem();
        item.setExpectedQuantity(10);

        transfer.addItem(item);

        assertThat(transfer.getItems()).hasSize(1);
        assertThat(item.getTransfer()).isEqualTo(transfer);
    }

    @Test
    void shouldRemoveItemFromTransfer() {
        Transfer transfer = new Transfer();
        TransferItem item = new TransferItem();
        transfer.addItem(item);

        transfer.removeItem(item);

        assertThat(transfer.getItems()).isEmpty();
        assertThat(item.getTransfer()).isNull();
    }
}
