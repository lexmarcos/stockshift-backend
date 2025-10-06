package com.stockshift.backend.domain.stock.exception;

import java.util.UUID;

public class StockEventNotFoundException extends RuntimeException {
    public StockEventNotFoundException(UUID id) {
        super("stock-event-not-found");
    }
}
