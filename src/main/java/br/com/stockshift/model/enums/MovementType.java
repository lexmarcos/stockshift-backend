package br.com.stockshift.model.enums;

public enum MovementType {
    PURCHASE,   // Buying from supplier
    SALE,       // Selling to customer
    TRANSFER,   // Moving between warehouses
    ADJUSTMENT, // Inventory adjustment (loss, damage, etc)
    RETURN      // Product return
}
