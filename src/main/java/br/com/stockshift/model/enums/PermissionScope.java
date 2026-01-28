package br.com.stockshift.model.enums;

public enum PermissionScope {
    ALL("Todos"),
    OWN_WAREHOUSE("Próprio Depósito"),
    OWN("Próprio");

    private final String displayName;

    PermissionScope(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
