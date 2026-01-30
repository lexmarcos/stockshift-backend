package br.com.stockshift.model.enums;

public enum DiscrepancyResolution {
    WRITE_OFF,       // Write off as loss
    FOUND,           // Item was found, create manual entry
    RETURN_TRANSIT,  // Return to origin (creates reverse transfer)
    ACCEPTED         // Accept excess with audit flag
}
