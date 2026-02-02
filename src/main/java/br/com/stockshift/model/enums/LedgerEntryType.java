package br.com.stockshift.model.enums;

public enum LedgerEntryType {
    PURCHASE_IN(false),
    ADJUSTMENT_IN(false),
    ADJUSTMENT_OUT(true),
    TRANSFER_OUT(true),
    TRANSFER_CANCELLED(false),
    TRANSFER_IN(false),
    TRANSFER_IN_DISCREPANCY(true);

    private final boolean debit;

    LedgerEntryType(boolean debit) {
        this.debit = debit;
    }

    public boolean isDebit() {
        return debit;
    }

    public boolean isCredit() {
        return !debit;
    }
}
