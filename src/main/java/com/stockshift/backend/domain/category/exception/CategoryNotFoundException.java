package com.stockshift.backend.domain.category.exception;

import java.util.UUID;

public class CategoryNotFoundException extends RuntimeException {
    
    public CategoryNotFoundException(UUID id) {
        super("Category not found with id: " + id);
    }

    public CategoryNotFoundException(String name) {
        super("Category not found with name: " + name);
    }
}
