package com.stockshift.backend.application.exception;

public class DuplicateGtinException extends RuntimeException {
    public DuplicateGtinException(String gtin) {
        super("GTIN '" + gtin + "' already exists");
    }
}
