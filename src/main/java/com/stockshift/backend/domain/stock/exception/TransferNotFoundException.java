package com.stockshift.backend.domain.stock.exception;

import java.util.UUID;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(UUID id) {
        super("Transfer not found with id: " + id);
    }
}
