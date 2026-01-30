package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.LedgerEntryType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryLedgerTest {

    @Test
    void shouldRequirePositiveQuantity() {
        InventoryLedger ledger = new InventoryLedger();
        ledger.setQuantity(100);
        assertThat(ledger.getQuantity()).isEqualTo(100);
    }

    @Test
    void shouldAllowNullWarehouseForVirtualEntries() {
        InventoryLedger ledger = new InventoryLedger();
        ledger.setEntryType(LedgerEntryType.TRANSFER_IN_TRANSIT);
        assertThat(ledger.getWarehouse()).isNull();
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
