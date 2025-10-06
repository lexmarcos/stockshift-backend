package com.stockshift.backend.domain.stock.exception;

public class StockExpiredItemMovementBlockedException extends RuntimeException {
    public StockExpiredItemMovementBlockedException() {
        super("expired-item-movement-blocked");
    }
}
