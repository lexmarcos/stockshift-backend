package com.stockshift.backend.domain.stock.exception;

public class StockIdempotencyConflictException extends RuntimeException {
    public StockIdempotencyConflictException() {
        super("idempotency-conflict");
    }
}
