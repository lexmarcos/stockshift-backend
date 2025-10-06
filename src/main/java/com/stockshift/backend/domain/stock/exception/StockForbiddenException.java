package com.stockshift.backend.domain.stock.exception;

public class StockForbiddenException extends RuntimeException {
    public StockForbiddenException() {
        super("forbidden");
    }
}
