package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerEntryTypeTest {

    @Test
    void shouldHaveAllRequiredEntryTypes() {
        assertThat(LedgerEntryType.values()).containsExactlyInAnyOrder(
                LedgerEntryType.PURCHASE_IN,
                LedgerEntryType.ADJUSTMENT_IN,
                LedgerEntryType.ADJUSTMENT_OUT,
                LedgerEntryType.TRANSFER_OUT,
                LedgerEntryType.TRANSFER_CANCELLED,
                LedgerEntryType.TRANSFER_IN,
                LedgerEntryType.TRANSFER_IN_DISCREPANCY,
                LedgerEntryType.USAGE_OUT,
                LedgerEntryType.GIFT_OUT,
                LedgerEntryType.LOSS_OUT,
                LedgerEntryType.DAMAGE_OUT,
                LedgerEntryType.STOCK_MOVEMENT_IN,
                LedgerEntryType.STOCK_MOVEMENT_OUT);
    }

    @Test
    void shouldIdentifyDebitEntryTypes() {
        assertThat(LedgerEntryType.ADJUSTMENT_OUT.isDebit()).isTrue();
    }

    @Test
    void shouldIdentifyCreditEntryTypes() {
        assertThat(LedgerEntryType.PURCHASE_IN.isCredit()).isTrue();
        assertThat(LedgerEntryType.ADJUSTMENT_IN.isCredit()).isTrue();
    }
}
