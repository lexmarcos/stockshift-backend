package com.stockshift.backend.domain.stock.exception;

public class SameWarehouseTransferException extends RuntimeException {
    public SameWarehouseTransferException() {
        super("Origin and destination warehouses must be different");
    }
}
