package com.stockshift.backend.domain.warehouse.exception;

import java.util.UUID;

public class WarehouseNotFoundException extends RuntimeException {
    
    public WarehouseNotFoundException(UUID id) {
        super("Warehouse not found with id: " + id);
    }

    public WarehouseNotFoundException(String code) {
        super("Warehouse not found with code: " + code);
    }
}
