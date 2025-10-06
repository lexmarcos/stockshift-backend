package com.stockshift.backend.domain.stock.exception;

public class StockVariantInactiveException extends RuntimeException {
    public StockVariantInactiveException() {
        super("invalid-payload: variant-inactive");
    }
}
