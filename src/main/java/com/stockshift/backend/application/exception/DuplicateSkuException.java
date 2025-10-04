package com.stockshift.backend.application.exception;

public class DuplicateSkuException extends RuntimeException {
    public DuplicateSkuException(String sku) {
        super("SKU '" + sku + "' already exists");
    }
}
