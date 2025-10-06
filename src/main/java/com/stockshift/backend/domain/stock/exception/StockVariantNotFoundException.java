package com.stockshift.backend.domain.stock.exception;

public class StockVariantNotFoundException extends RuntimeException {
    public StockVariantNotFoundException() {
        super("variant-not-found");
    }
}
