package com.stockshift.backend.domain.brand.exception;

public class BrandAlreadyExistsException extends RuntimeException {
    
    public BrandAlreadyExistsException(String name) {
        super("Brand already exists with name: " + name);
    }
}
