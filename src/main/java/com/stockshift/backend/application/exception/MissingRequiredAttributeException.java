package com.stockshift.backend.application.exception;

public class MissingRequiredAttributeException extends RuntimeException {
    public MissingRequiredAttributeException(String definitionCode) {
        super("Required attribute definition '" + definitionCode + "' is missing");
    }

    public MissingRequiredAttributeException(String message, String details) {
        super(message + ": " + details);
    }
}
