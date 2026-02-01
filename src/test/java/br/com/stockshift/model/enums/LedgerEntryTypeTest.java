package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerEntryTypeTest {

    @Test
    void shouldHaveAllRequiredEntryTypes() {
        assertThat(LedgerEntryType.values()).containsExactlyInAnyOrder(
            LedgerEntryType.PURCHASE_IN,
            LedgerEntryType.ADJUSTMENT_IN,
            LedgerEntryType.ADJUSTMENT_OUT
        );
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
