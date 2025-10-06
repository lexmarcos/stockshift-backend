package com.stockshift.backend.domain.stock.exception;

public class StockConcurrencyConflictException extends RuntimeException {
    public StockConcurrencyConflictException() {
        super("concurrency-conflict");
    }
}
