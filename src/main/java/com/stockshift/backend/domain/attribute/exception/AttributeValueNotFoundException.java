package com.stockshift.backend.domain.attribute.exception;

import java.util.UUID;

public class AttributeValueNotFoundException extends RuntimeException {
    
    public AttributeValueNotFoundException(UUID id) {
        super("Attribute value not found with id: " + id);
    }

    public AttributeValueNotFoundException(UUID definitionId, String value) {
        super("Attribute value '" + value + "' not found for definition: " + definitionId);
    }
}
