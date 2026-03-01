package br.com.stockshift.model.enums;

public enum LedgerEntryType {
    PURCHASE_IN(false),
    ADJUSTMENT_IN(false),
    ADJUSTMENT_OUT(true),
    TRANSFER_OUT(true),
    TRANSFER_CANCELLED(false),
    TRANSFER_IN(false),
    TRANSFER_IN_DISCREPANCY(true),
    USAGE_OUT(true),
    GIFT_OUT(true),
    LOSS_OUT(true),
    DAMAGE_OUT(true),
    STOCK_MOVEMENT_IN(false),
    STOCK_MOVEMENT_OUT(true);

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
