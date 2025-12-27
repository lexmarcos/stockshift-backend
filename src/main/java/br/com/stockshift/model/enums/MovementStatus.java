package br.com.stockshift.model.enums;

public enum MovementStatus {
    PENDING,    // Created but not executed
    IN_TRANSIT, // Product left origin, not arrived at destination
    COMPLETED,  // Movement completed
    CANCELLED   // Movement cancelled
}
