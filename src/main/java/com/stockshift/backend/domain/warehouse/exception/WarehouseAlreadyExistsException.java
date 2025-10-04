package com.stockshift.backend.domain.warehouse.exception;

public class WarehouseAlreadyExistsException extends RuntimeException {
    
    public WarehouseAlreadyExistsException(String code) {
        super("Warehouse already exists with code: " + code);
    }
}
