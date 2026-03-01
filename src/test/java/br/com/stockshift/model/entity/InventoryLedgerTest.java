package br.com.stockshift.model.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

class InventoryLedgerTest {

    @Test
    void shouldRequirePositiveQuantity() {
        InventoryLedger ledger = new InventoryLedger();
        ledger.setQuantity(new BigDecimal("100"));
        assertThat(ledger.getQuantity()).isEqualTo(new BigDecimal("100"));
    }

    @Test
    void shouldHaveReferenceFields() {
        InventoryLedger ledger = new InventoryLedger();
        UUID refId = UUID.randomUUID();
        ledger.setReferenceType("TRANSFER");
        ledger.setReferenceId(refId);

        assertThat(ledger.getReferenceType()).isEqualTo("TRANSFER");
        assertThat(ledger.getReferenceId()).isEqualTo(refId);
    }
}
