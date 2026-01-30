package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerEntryTypeTest {

    @Test
    void shouldHaveAllRequiredEntryTypes() {
        assertThat(LedgerEntryType.values()).containsExactlyInAnyOrder(
            LedgerEntryType.PURCHASE_IN,
            LedgerEntryType.SALE_OUT,
            LedgerEntryType.ADJUSTMENT_IN,
            LedgerEntryType.ADJUSTMENT_OUT,
            LedgerEntryType.TRANSFER_OUT,
            LedgerEntryType.TRANSFER_IN_TRANSIT,
            LedgerEntryType.TRANSFER_IN,
            LedgerEntryType.TRANSFER_TRANSIT_CONSUMED,
            LedgerEntryType.TRANSFER_LOSS,
            LedgerEntryType.RETURN_IN
        );
    }

    @Test
    void shouldIdentifyDebitEntryTypes() {
        assertThat(LedgerEntryType.SALE_OUT.isDebit()).isTrue();
        assertThat(LedgerEntryType.ADJUSTMENT_OUT.isDebit()).isTrue();
        assertThat(LedgerEntryType.TRANSFER_OUT.isDebit()).isTrue();
        assertThat(LedgerEntryType.TRANSFER_TRANSIT_CONSUMED.isDebit()).isTrue();
        assertThat(LedgerEntryType.TRANSFER_LOSS.isDebit()).isTrue();
    }

    @Test
    void shouldIdentifyCreditEntryTypes() {
        assertThat(LedgerEntryType.PURCHASE_IN.isCredit()).isTrue();
        assertThat(LedgerEntryType.ADJUSTMENT_IN.isCredit()).isTrue();
        assertThat(LedgerEntryType.TRANSFER_IN.isCredit()).isTrue();
        assertThat(LedgerEntryType.TRANSFER_IN_TRANSIT.isCredit()).isTrue();
        assertThat(LedgerEntryType.RETURN_IN.isCredit()).isTrue();
    }
}
