package com.stockshift.backend.domain.product.exception;

public class DuplicateAttributeCombinationException extends RuntimeException {
    
    public DuplicateAttributeCombinationException() {
        super("A variant with this attribute combination already exists for this product");
    }
}
