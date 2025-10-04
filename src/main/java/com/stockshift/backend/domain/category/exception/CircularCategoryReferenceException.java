package com.stockshift.backend.domain.category.exception;

public class CircularCategoryReferenceException extends RuntimeException {
    
    public CircularCategoryReferenceException() {
        super("Circular reference detected: a category cannot be its own parent or descendant");
    }
}
