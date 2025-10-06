package com.stockshift.backend.domain.stock.exception;

public class StockInsufficientQuantityException extends RuntimeException {
    public StockInsufficientQuantityException() {
        super("insufficient-stock");
    }
}
