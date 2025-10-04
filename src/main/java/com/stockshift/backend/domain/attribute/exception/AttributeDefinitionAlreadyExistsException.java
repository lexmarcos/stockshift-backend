package com.stockshift.backend.domain.attribute.exception;

public class AttributeDefinitionAlreadyExistsException extends RuntimeException {
    
    public AttributeDefinitionAlreadyExistsException(String name) {
        super("Attribute definition already exists with name: " + name);
    }
}
