package com.stockshift.backend.api.exception;

public class BrandNotFoundException extends RuntimeException {
    public BrandNotFoundException(String message) {
        super(message);
    }
}
