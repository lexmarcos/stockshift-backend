package br.com.stockshift.model.enums;

public enum TransferRole {
    OUTBOUND,   // User belongs to source warehouse
    INBOUND,    // User belongs to destination warehouse
    NONE;       // User has no access to either warehouse

    public boolean isSourceRole() {
        return this == OUTBOUND;
    }

    public boolean isDestinationRole() {
        return this == INBOUND;
    }

    public boolean hasAccess() {
        return this != NONE;
    }
}
