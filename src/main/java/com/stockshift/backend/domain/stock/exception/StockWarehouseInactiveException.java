package com.stockshift.backend.domain.stock.exception;

public class StockWarehouseInactiveException extends RuntimeException {
    public StockWarehouseInactiveException() {
        super("invalid-payload: warehouse-inactive");
    }
}
