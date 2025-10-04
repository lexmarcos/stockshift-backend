package com.stockshift.backend.domain.brand.exception;

import java.util.UUID;

public class BrandNotFoundException extends RuntimeException {
    
    public BrandNotFoundException(UUID id) {
        super("Brand not found with id: " + id);
    }

    public BrandNotFoundException(String name) {
        super("Brand not found with name: " + name);
    }
}
