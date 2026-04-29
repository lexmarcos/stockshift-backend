package br.com.stockshift.service;

import br.com.stockshift.dto.role.RoleRequest;
import br.com.stockshift.dto.role.RoleResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.repository.PermissionRepository;
import br.com.stockshift.repository.RoleRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.audit.AuditService;
import br.com.stockshift.service.audit.AuditSnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private AuditSnapshotService auditSnapshotService;

    private RoleService service;
    private UUID tenantId;
    private Permission permission;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        service = new RoleService(roleRepository, permissionRepository, auditService, auditSnapshotService);
        permission = permission("sales:read");
        when(auditSnapshotService.snapshot(any())).thenReturn(Map.of("name", "role"));
        when(auditSnapshotService.diff(any(), any())).thenReturn(List.of("name"));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            if (role.getId() == null) {
                role.setId(UUID.randomUUID());
            }
            return role;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createShouldSanitizePermissionsAndAudit() {
        RoleRequest request = new RoleRequest("<b>Seller</b>", "<i>Can sell</i>", Set.of(permission.getId()));
        when(roleRepository.findByTenantIdAndName(tenantId, request.getName())).thenReturn(Optional.empty());
        when(permissionRepository.findById(permission.getId())).thenReturn(Optional.of(permission));

        RoleResponse response = service.create(request);

        assertThat(response.getName()).contains("&lt;b&gt;Seller&lt;/b&gt;");
        assertThat(response.getPermissions()).extracting(RoleResponse.PermissionResponse::getCode)
                .containsExactly("sales:read");
        verify(auditService, atLeastOnce()).record(any());
    }

    @Test
    void createShouldRejectDuplicateNameAndMissingPermission() {
        RoleRequest duplicate = new RoleRequest("Seller", "Can sell", Set.of());
        when(roleRepository.findByTenantIdAndName(tenantId, "Seller"))
                .thenReturn(Optional.of(role("Seller", false)));

        assertThatThrownBy(() -> service.create(duplicate))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        RoleRequest missingPermission = new RoleRequest("Other", "Other", Set.of(permission.getId()));
        when(roleRepository.findByTenantIdAndName(tenantId, "Other")).thenReturn(Optional.empty());
        when(permissionRepository.findById(permission.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(missingPermission))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findUpdateAndDeleteShouldRespectSystemRoleAndDuplicates() {
        Role role = role("Seller", false);
        Role systemRole = role("ADMIN", true);
        Permission updatePermission = permission("roles:update");
        when(roleRepository.findByTenantId(tenantId)).thenReturn(List.of(role, systemRole));
        when(permissionRepository.findById(updatePermission.getId())).thenReturn(Optional.of(updatePermission));

        assertThat(service.findAll()).extracting(RoleResponse::getName).containsExactly("Seller", "ADMIN");
        assertThat(service.findById(role.getId()).getName()).isEqualTo("Seller");

        when(roleRepository.findByTenantIdAndName(tenantId, "Manager")).thenReturn(Optional.empty());
        RoleResponse updated = service.update(role.getId(),
                new RoleRequest("Manager", "Updated", Set.of(updatePermission.getId())));

        assertThat(updated.getName()).isEqualTo("Manager");
        assertThat(updated.getPermissions()).extracting(RoleResponse.PermissionResponse::getCode)
                .containsExactly("roles:update");

        assertThatThrownBy(() -> service.update(systemRole.getId(), new RoleRequest("Root", "Root", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("System roles cannot be modified");

        when(roleRepository.findByTenantIdAndName(tenantId, "Existing"))
                .thenReturn(Optional.of(systemRole));
        assertThatThrownBy(() -> service.update(role.getId(), new RoleRequest("Existing", "Dup", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        service.delete(role.getId());
        verify(roleRepository).delete(role);
        verify(auditService, atLeastOnce()).record(any());

        assertThatThrownBy(() -> service.delete(systemRole.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("System roles cannot be deleted");
        verify(roleRepository, never()).delete(systemRole);
    }

    @Test
    void findByIdShouldThrowWhenRoleIsOutsideTenantList() {
        when(roleRepository.findByTenantId(tenantId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Role role(String name, boolean system) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setTenantId(tenantId);
        role.setName(name);
        role.setDescription(name + " description");
        role.setIsSystemRole(system);
        role.setPermissions(Set.of(permission));
        return role;
    }

    private Permission permission(String code) {
        Permission permission = new Permission();
        permission.setId(UUID.randomUUID());
        permission.setCode(code);
        permission.setDescription(code + " description");
        return permission;
    }
}
