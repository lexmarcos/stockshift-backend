package br.com.stockshift.model.enums;

public enum LedgerEntryType {
    PURCHASE_IN(false),
    SALE_OUT(true),
    ADJUSTMENT_IN(false),
    ADJUSTMENT_OUT(true),
    TRANSFER_OUT(true),
    TRANSFER_IN_TRANSIT(false),
    TRANSFER_IN(false),
    TRANSFER_TRANSIT_CONSUMED(true),
    TRANSFER_LOSS(true),
    RETURN_IN(false);

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
