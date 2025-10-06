package com.stockshift.backend.domain.stock.exception;

public class TransferNotDraftException extends RuntimeException {
    public TransferNotDraftException() {
        super("Transfer must be in DRAFT status to perform this operation");
    }
}
