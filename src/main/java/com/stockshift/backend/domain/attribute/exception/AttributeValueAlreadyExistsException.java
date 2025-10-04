package com.stockshift.backend.domain.attribute.exception;

public class AttributeValueAlreadyExistsException extends RuntimeException {
    
    public AttributeValueAlreadyExistsException(String definitionName, String value) {
        super("Attribute value '" + value + "' already exists for definition: " + definitionName);
    }

    public AttributeValueAlreadyExistsException(String message) {
        super(message);
    }
}
