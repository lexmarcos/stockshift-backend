package com.stockshift.backend.domain.product.exception;

public class ProductVariantAlreadyExistsException extends RuntimeException {
    
    public ProductVariantAlreadyExistsException(String identifier, String value) {
        super("Product variant already exists with " + identifier + ": " + value);
    }
}
