package com.stockshift.backend.domain.stock.exception;

public class StockInvalidPayloadException extends RuntimeException {
    public StockInvalidPayloadException(String detail) {
        super("invalid-payload" + (detail != null ? ": " + detail : ""));
    }
}
