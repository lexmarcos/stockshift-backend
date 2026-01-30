package br.com.stockshift.model.enums;

/**
 * Enumeration of event types that can occur during a stock transfer lifecycle.
 */
public enum TransferEventType {
    CREATED,
    UPDATED,
    DISPATCHED,
    VALIDATION_STARTED,
    ITEM_SCANNED,
    COMPLETED,
    COMPLETED_WITH_DISCREPANCY,
    CANCELLED,
    DISCREPANCY_RESOLVED
}
