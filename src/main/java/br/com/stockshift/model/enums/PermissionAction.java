package br.com.stockshift.model.enums;

public enum PermissionAction {
    CREATE("Criar"),
    READ("Visualizar"),
    UPDATE("Atualizar"),
    DELETE("Excluir"),
    APPROVE("Aprovar");

    private final String displayName;

    PermissionAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
