package com.stockshift.backend.api.exception;

public class BrandAlreadyExistsException extends RuntimeException {
    public BrandAlreadyExistsException(String message) {
        super(message);
    }
}
