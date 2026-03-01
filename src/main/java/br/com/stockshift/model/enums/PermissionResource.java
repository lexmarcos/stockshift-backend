package br.com.stockshift.model.enums;

public enum PermissionResource {
    PRODUCT("Produto"),
    STOCK("Estoque"),
    USER("Usuário"),
    REPORT("Relatório"),
    WAREHOUSE("Depósito"),
    TRANSFER("Transferência"),
    STOCK_MOVEMENT("Movimentação de Estoque");

    private final String displayName;

    PermissionResource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
