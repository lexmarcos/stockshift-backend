package com.stockshift.backend.application.exception;

import java.util.UUID;

public class AttributeNotApplicableException extends RuntimeException {
    public AttributeNotApplicableException(String definitionCode, UUID categoryId) {
        super("Attribute definition '" + definitionCode + "' is not applicable to category " + categoryId);
    }
}
