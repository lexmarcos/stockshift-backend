package br.com.stockshift.model.enums;

public enum MovementStatus {
    PENDING,    // Created but not executed
    IN_TRANSIT, // Product left origin, not arrived at destination
    COMPLETED,  // Movement completed
    COMPLETED_WITH_DISCREPANCY, // Movement completed but with missing items
    CANCELLED   // Movement cancelled
}
