package br.com.stockshift.model.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BatchOriginTest {

    @Test
    void shouldHaveNullOriginFieldsByDefault() {
        Batch batch = new Batch();
        assertThat(batch.getOriginTransfer()).isNull();
        assertThat(batch.getOriginBatch()).isNull();
    }

    @Test
    void shouldTrackOriginTransfer() {
        Batch batch = new Batch();
        Transfer transfer = new Transfer();

        batch.setOriginTransfer(transfer);

        assertThat(batch.getOriginTransfer()).isEqualTo(transfer);
    }

    @Test
    void shouldTrackOriginBatch() {
        Batch originalBatch = new Batch();
        Batch newBatch = new Batch();

        newBatch.setOriginBatch(originalBatch);

        assertThat(newBatch.getOriginBatch()).isEqualTo(originalBatch);
    }
}
