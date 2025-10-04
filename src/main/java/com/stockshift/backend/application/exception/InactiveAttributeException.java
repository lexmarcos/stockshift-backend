package com.stockshift.backend.application.exception;

public class InactiveAttributeException extends RuntimeException {
    public InactiveAttributeException(String type, String identifier) {
        super("Cannot use inactive " + type + ": " + identifier);
    }
}
