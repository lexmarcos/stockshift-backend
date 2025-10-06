package com.stockshift.backend.domain.stock.exception;

public class TransferIdempotencyConflictException extends RuntimeException {
    public TransferIdempotencyConflictException() {
        super("Idempotency key already used with different request data");
    }
}
