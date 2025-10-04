package com.stockshift.backend.domain.attribute.exception;

import java.util.UUID;

public class AttributeDefinitionNotFoundException extends RuntimeException {
    
    public AttributeDefinitionNotFoundException(UUID id) {
        super("Attribute definition not found with id: " + id);
    }

    public AttributeDefinitionNotFoundException(String name) {
        super("Attribute definition not found with name: " + name);
    }
}
