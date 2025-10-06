package com.stockshift.backend.domain.stock.exception;

public class StockWarehouseNotFoundException extends RuntimeException {
    public StockWarehouseNotFoundException() {
        super("warehouse-not-found");
    }
}
