package br.com.stockshift.model.enums;

public enum LedgerEntryType {
    PURCHASE_IN(false),
    ADJUSTMENT_IN(false),
    ADJUSTMENT_OUT(true);

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
