package com.stockshift.backend.domain.product.exception;

import java.util.UUID;

public class ProductVariantNotFoundException extends RuntimeException {
    
    public ProductVariantNotFoundException(UUID id) {
        super("Product variant not found with id: " + id);
    }

    public ProductVariantNotFoundException(String identifier) {
        super("Product variant not found with identifier: " + identifier);
    }
}
