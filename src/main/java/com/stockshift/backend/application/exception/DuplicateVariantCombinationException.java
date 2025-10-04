package com.stockshift.backend.application.exception;

public class DuplicateVariantCombinationException extends RuntimeException {
    public DuplicateVariantCombinationException(String message) {
        super(message);
    }
}
