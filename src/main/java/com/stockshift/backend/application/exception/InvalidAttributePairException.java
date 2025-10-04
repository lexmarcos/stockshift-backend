package com.stockshift.backend.application.exception;

import java.util.UUID;

public class InvalidAttributePairException extends RuntimeException {
    public InvalidAttributePairException(UUID definitionId, UUID valueId) {
        super("Attribute value " + valueId + " does not belong to definition " + definitionId);
    }

    public InvalidAttributePairException(String message) {
        super(message);
    }
}
