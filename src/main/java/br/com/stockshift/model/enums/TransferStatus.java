package br.com.stockshift.model.enums;

public enum TransferStatus {
    DRAFT,
    IN_TRANSIT,
    VALIDATION_IN_PROGRESS,
    COMPLETED,
    COMPLETED_WITH_DISCREPANCY,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_WITH_DISCREPANCY || this == CANCELLED;
    }
}
