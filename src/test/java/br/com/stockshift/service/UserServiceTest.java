package br.com.stockshift.service;

import br.com.stockshift.dto.user.CreateUserRequest;
import br.com.stockshift.dto.user.CreateUserResponse;
import br.com.stockshift.dto.user.UpdateUserRequest;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.UserRoleWarehouse;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.RoleRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.UserRoleWarehouseRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.UserPrincipal;
import br.com.stockshift.service.audit.AuditService;
import br.com.stockshift.service.audit.AuditSnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collection;
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
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private UserRoleWarehouseRepository userRoleWarehouseRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;
    @Mock
    private AuditSnapshotService auditSnapshotService;

    private UserService service;
    private UUID tenantId;
    private UUID currentUserId;
    private Role role;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();
        role = role(tenantId, "ADMIN");
        warehouse = warehouse(tenantId, "Main");
        service = new UserService(userRepository, roleRepository, warehouseRepository,
                userRoleWarehouseRepository, passwordEncoder, auditService, auditSnapshotService);
        setPrincipal(currentUserId);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(auditSnapshotService.snapshot(any())).thenReturn(Map.of("id", "value"));
        when(auditSnapshotService.diff(any(), any())).thenReturn(List.of("fullName"));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUserShouldValidateScopePersistAssignmentsAndAudit() {
        CreateUserRequest request = new CreateUserRequest("USER@EXAMPLE.COM", "<b>Alice</b>",
                Set.of(role.getId()), Set.of(warehouse.getId()));
        when(userRepository.findByTenantIdAndEmail(tenantId, request.getEmail())).thenReturn(Optional.empty());
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(warehouseRepository.findByTenantIdAndId(tenantId, warehouse.getId())).thenReturn(Optional.of(warehouse));

        CreateUserResponse response = service.createUser(request);

        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getTemporaryPassword()).isNotBlank();
        assertThat(response.getRoles()).containsExactly("ADMIN");
        assertThat(response.getWarehouses()).containsExactly("Main");
        ArgumentCaptor<Collection<UserRoleWarehouse>> assignmentsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(userRoleWarehouseRepository).saveAll(assignmentsCaptor.capture());
        assertThat(assignmentsCaptor.getValue()).hasSize(1);
        verify(auditService, atLeastOnce()).record(any());
    }

    @Test
    void createUserShouldRejectDuplicateEmailRoleFromAnotherTenantAndInactiveWarehouse() {
        CreateUserRequest request = new CreateUserRequest("user@example.com", "Alice",
                Set.of(role.getId()), Set.of(warehouse.getId()));
        when(userRepository.findByTenantIdAndEmail(tenantId, request.getEmail()))
                .thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email already registered");

        when(userRepository.findByTenantIdAndEmail(tenantId, request.getEmail())).thenReturn(Optional.empty());
        Role foreignRole = role(UUID.randomUUID(), "OTHER");
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(foreignRole));

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong");

        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        warehouse.setIsActive(false);
        when(warehouseRepository.findByTenantIdAndId(tenantId, warehouse.getId())).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void listFindUpdateAndDeleteShouldUseCurrentTenantAndAuditChanges() {
        User user = user("worker@example.com", "Worker", role, warehouse);
        UpdateUserRequest update = new UpdateUserRequest("Updated", false, Set.of(role.getId()), Set.of(warehouse.getId()));
        when(userRepository.findAllByTenantId(tenantId)).thenReturn(List.of(user));
        when(userRepository.findByTenantIdAndId(tenantId, user.getId())).thenReturn(Optional.of(user));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(warehouseRepository.findByTenantIdAndId(tenantId, warehouse.getId())).thenReturn(Optional.of(warehouse));

        assertThat(service.listUsers()).extracting("email").containsExactly("worker@example.com");
        assertThat(service.findById(user.getId()).getFullName()).isEqualTo("Worker");

        assertThat(service.updateUser(user.getId(), update).getFullName()).isEqualTo("Updated");
        assertThat(user.getIsActive()).isFalse();
        verify(userRoleWarehouseRepository).deleteByUserId(user.getId());

        service.deleteUser(user.getId());

        verify(userRepository).delete(user);
        verify(auditService, atLeastOnce()).record(any());
    }

    @Test
    void deleteUserShouldRejectSelfDeletion() {
        User user = user("me@example.com", "Me", role, warehouse);
        user.setId(currentUserId);
        when(userRepository.findByTenantIdAndId(tenantId, currentUserId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.deleteUser(currentUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot delete your own account");

        verify(userRepository, never()).delete(any());
    }

    private void setPrincipal(UUID userId) {
        UserPrincipal principal = new UserPrincipal(userId, tenantId, "admin@example.com", "password",
                true, List.of(), Set.of(warehouse != null ? warehouse.getId() : UUID.randomUUID()), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "password", principal.getAuthorities()));
    }

    private User user(String email, String fullName, Role role, Warehouse warehouse) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPassword("password");
        user.setFullName(fullName);
        user.setIsActive(true);
        user.setMustChangePassword(false);
        user.setRoles(Set.of(role));
        user.setWarehouses(Set.of(warehouse));
        return user;
    }

    private Role role(UUID tenantId, String name) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setTenantId(tenantId);
        role.setName(name);
        role.setDescription(name + " role");
        role.setIsSystemRole(false);
        return role;
    }

    private Warehouse warehouse(UUID tenantId, String name) {
        Warehouse wh = new Warehouse();
        wh.setId(UUID.randomUUID());
        wh.setTenantId(tenantId);
        wh.setName(name);
        wh.setCode(name.toUpperCase());
        wh.setCity("Recife");
        wh.setState("PE");
        wh.setIsActive(true);
        return wh;
    }
}
