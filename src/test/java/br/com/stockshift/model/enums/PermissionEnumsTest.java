package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionEnumsTest {

    @Test
    void permissionActionsShouldExposeDisplayNames() {
        assertThat(PermissionAction.values()).hasSize(9);
        assertThat(PermissionAction.CREATE.getDisplayName()).isEqualTo("Criar");
        assertThat(PermissionAction.READ.getDisplayName()).isEqualTo("Visualizar");
        assertThat(PermissionAction.UPDATE.getDisplayName()).isEqualTo("Atualizar");
        assertThat(PermissionAction.DELETE.getDisplayName()).isEqualTo("Excluir");
        assertThat(PermissionAction.APPROVE.getDisplayName()).isEqualTo("Aprovar");
        assertThat(PermissionAction.EXECUTE.getDisplayName()).isEqualTo("Executar");
        assertThat(PermissionAction.VALIDATE.getDisplayName()).isEqualTo("Validar");
        assertThat(PermissionAction.RESOLVE.getDisplayName()).isEqualTo("Resolver");
        assertThat(PermissionAction.CANCEL.getDisplayName()).isEqualTo("Cancelar");
    }

    @Test
    void permissionResourcesShouldExposeDisplayNames() {
        assertThat(PermissionResource.values()).hasSize(7);
        assertThat(PermissionResource.PRODUCT.getDisplayName()).isEqualTo("Produto");
        assertThat(PermissionResource.STOCK.getDisplayName()).isEqualTo("Estoque");
        assertThat(PermissionResource.USER.getDisplayName()).isEqualTo("Usuário");
        assertThat(PermissionResource.REPORT.getDisplayName()).isEqualTo("Relatório");
        assertThat(PermissionResource.WAREHOUSE.getDisplayName()).isEqualTo("Depósito");
        assertThat(PermissionResource.TRANSFER.getDisplayName()).isEqualTo("Transferência");
        assertThat(PermissionResource.STOCK_MOVEMENT.getDisplayName()).isEqualTo("Movimentação de Estoque");
    }

    @Test
    void permissionScopesShouldExposeDisplayNames() {
        assertThat(PermissionScope.values()).hasSize(5);
        assertThat(PermissionScope.ALL.getDisplayName()).isEqualTo("Todos");
        assertThat(PermissionScope.OWN_WAREHOUSE.getDisplayName()).isEqualTo("Próprio Depósito");
        assertThat(PermissionScope.OWN.getDisplayName()).isEqualTo("Próprio");
        assertThat(PermissionScope.TENANT.getDisplayName()).isEqualTo("Inquilino");
        assertThat(PermissionScope.OWNED.getDisplayName()).isEqualTo("Próprio");
    }
}
