package br.com.stockshift.service;

import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.repository.UserRoleWarehouseRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.security.UserPrincipal;
import br.com.stockshift.security.WarehouseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseAccessServiceTest {

    @Mock
    private UserRoleWarehouseRepository userRoleWarehouseRepository;

    private WarehouseAccessService warehouseAccessService;
    private PermissionResolverService permissionResolverService;
    private UUID tenantId;
    private UUID userId;
    private UUID warehouseId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        warehouseAccessService = new WarehouseAccessService(userRoleWarehouseRepository);
        permissionResolverService = new PermissionResolverService(userRoleWarehouseRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        WarehouseContext.clear();
    }

    @Test
    void warehouseAccessShouldUsePrincipalContextRepositoryAndFallbackTenant() {
        setPrincipal(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(userRoleWarehouseRepository.existsByUserIdAndWarehouseId(userId, warehouseId)).thenReturn(true);
        when(userRoleWarehouseRepository.findWarehouseIdsByUserId(userId)).thenReturn(Set.of(warehouseId));

        assertThat(warehouseAccessService.canAccessWarehouse(userId, warehouseId)).isTrue();
        assertThat(warehouseAccessService.canAccessWarehouse(null, warehouseId)).isFalse();
        assertThat(warehouseAccessService.getUserWarehouseIds()).containsExactly(warehouseId);
        assertThat(warehouseAccessService.hasFullAccess()).isTrue();
        assertThat(warehouseAccessService.getTenantId()).isEqualTo(tenantId);

        warehouseAccessService.validateWarehouseAccess(warehouseId);
        warehouseAccessService.validateWarehouseAccessOrThrow();

        SecurityContextHolder.clearContext();
        TenantContext.setTenantId(tenantId);
        assertThat(warehouseAccessService.getTenantId()).isEqualTo(tenantId);
        assertThat(warehouseAccessService.hasFullAccess()).isFalse();
        assertThat(warehouseAccessService.getUserWarehouseIds()).isEmpty();
    }

    @Test
    void warehouseAccessShouldRejectMissingWrongScopedAndUnassignedWarehouses() {
        setPrincipal(List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThatThrownBy(() -> warehouseAccessService.validateWarehouseAccess(null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("required");

        WarehouseContext.setWarehouseId(UUID.randomUUID());
        assertThatThrownBy(() -> warehouseAccessService.validateWarehouseAccess(warehouseId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("outside current token scope");

        WarehouseContext.clear();
        when(userRoleWarehouseRepository.existsByUserIdAndWarehouseId(userId, warehouseId)).thenReturn(false);
        assertThatThrownBy(() -> warehouseAccessService.validateWarehouseAccess(warehouseId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("don't have access");

        when(userRoleWarehouseRepository.findWarehouseIdsByUserId(userId)).thenReturn(Set.of());
        assertThatThrownBy(() -> warehouseAccessService.validateWarehouseAccessOrThrow())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("No warehouse access");
    }

    @Test
    void warehouseAccessShouldLetFullAccessBypassActiveWarehouseScope() {
        setPrincipal(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        WarehouseContext.setWarehouseId(UUID.randomUUID());

        warehouseAccessService.validateWarehouseAccess(warehouseId);
    }

    @Test
    void permissionResolverShouldReturnWildcardCodesRoleNamesAndLegacyCodes() {
        Role admin = role("ADMIN");
        when(userRoleWarehouseRepository.findRolesByUserIdAndWarehouseId(userId, warehouseId))
                .thenReturn(Set.of(admin));
        assertThat(permissionResolverService.resolveUserPermissions(userId, warehouseId)).containsExactly("*");

        Role seller = role("Seller");
        Permission explicit = permission("Sales:Read", null, null);
        Permission legacyStock = permission(null, "stock", "update");
        Permission legacyTransferCancel = permission(null, "transfer", "cancel");
        Permission legacyCustom = permission(null, "custom", "run");
        Set<Permission> permissions = new HashSet<>();
        permissions.add(explicit);
        permissions.add(legacyStock);
        permissions.add(legacyTransferCancel);
        permissions.add(legacyCustom);
        permissions.add(null);
        seller.setPermissions(permissions);
        when(userRoleWarehouseRepository.findRolesByUserIdAndWarehouseId(userId, warehouseId))
                .thenReturn(Set.of(seller));

        assertThat(permissionResolverService.resolveUserPermissions(userId, warehouseId))
                .containsExactlyInAnyOrder("sales:read", "batches:update", "transfers:delete", "custom:run");
        assertThat(permissionResolverService.resolveUserRoleNames(userId, warehouseId)).containsExactly("Seller");
        assertThat(permissionResolverService.resolveUserPermissions(null, warehouseId)).isEmpty();
        assertThat(permissionResolverService.resolveUserRoleNames(userId, null)).isEmpty();
    }

    private void setPrincipal(List<SimpleGrantedAuthority> authorities) {
        UserPrincipal principal = new UserPrincipal(userId, tenantId, "user@example.com", "password",
                true, authorities, Set.of(warehouseId), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "password", authorities));
    }

    private Role role(String name) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setTenantId(tenantId);
        role.setName(name);
        role.setPermissions(Set.of());
        return role;
    }

    private Permission permission(String code, String resource, String action) {
        Permission permission = new Permission();
        permission.setId(UUID.randomUUID());
        permission.setCode(code);
        permission.setResource(resource);
        permission.setAction(action);
        return permission;
    }
}
