package com.stockshift.backend.domain.product.exception;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {
    
    public ProductNotFoundException(UUID id) {
        super("Product not found with id: " + id);
    }

    public ProductNotFoundException(String name) {
        super("Product not found with name: " + name);
    }
}
