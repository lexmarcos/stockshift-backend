package br.com.stockshift.service;

import br.com.stockshift.config.JwtProperties;
import br.com.stockshift.dto.auth.RegisterRequest;
import br.com.stockshift.dto.auth.RegisterResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.PermissionRepository;
import br.com.stockshift.repository.RoleRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.JwtTokenProvider;
import br.com.stockshift.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantServiceTest {

    private static final Set<String> MANAGER_PERMISSION_CODES = Set.of(
            "sales:create",
            "sales:read",
            "sales:cancel",
            "products:read",
            "products:create",
            "products:update",
            "products:delete",
            "products:analyze_image",
            "product_prompts:read",
            "product_prompts:create",
            "product_prompts:update",
            "product_prompts:delete",
            "brands:read",
            "brands:create",
            "brands:update",
            "brands:delete",
            "categories:read",
            "categories:create",
            "categories:update",
            "categories:delete",
            "batches:read",
            "batches:create",
            "batches:update",
            "batches:delete",
            "warehouses:read",
            "warehouses:create",
            "warehouses:update",
            "warehouses:delete",
            "stock_movements:read",
            "stock_movements:create",
            "transfers:read",
            "transfers:create",
            "transfers:update",
            "transfers:delete",
            "transfers:execute",
            "transfers:validate",
            "reports:read");

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuditService auditService;

    private TenantService service;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setAccessExpiration(3600L);
        service = new TenantService(tenantRepository, userRepository, roleRepository, permissionRepository, passwordEncoder,
                jwtTokenProvider, refreshTokenService, jwtProperties, auditService);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(UUID.randomUUID());
            return role;
        });
        when(permissionRepository.findByCodeIgnoreCase(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return Optional.of(permission(code));
        });
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
    }

    @Test
    void registerShouldCreateTenantAdminRoleUserTokensAndAudit() {
        RegisterRequest request = RegisterRequest.builder()
                .companyName("Acme")
                .email("admin@acme.com")
                .password("secret123")
                .build();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh");
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(tenantRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(jwtTokenProvider.generateAccessToken(any(), any(), eq("admin@acme.com"), anyList(), anyList()))
                .thenReturn("access");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn(refreshToken);

        RegisterResponse response = service.register(request);

        assertThat(response.getBusinessName()).isEqualTo("Acme");
        assertThat(response.getUserEmail()).isEqualTo("admin@acme.com");
        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        assertDefaultRolesCreated();
        verify(auditService).record(any());
    }

    @Test
    void registerShouldRejectDuplicateTenantOrUserEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .companyName("Acme")
                .email("admin@acme.com")
                .password("secret123")
                .build();
        when(tenantRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(new Tenant()));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email already registered");

        when(tenantRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email already registered");
    }

    private void assertDefaultRolesCreated() {
        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(3)).save(captor.capture());
        List<Role> roles = captor.getAllValues();

        assertThat(roles).extracting(Role::getName).containsExactly("ADMIN", "VENDEDOR", "GERENTE");
        assertThat(roles).allMatch(Role::getIsSystemRole);
        assertThat(roleByName(roles, "ADMIN").getPermissions()).isEmpty();
        assertThat(roleByName(roles, "VENDEDOR").getPermissions())
                .extracting(Permission::getCode)
                .containsExactlyInAnyOrder(
                        "sales:create",
                        "sales:read",
                        "products:read",
                        "batches:read",
                        "brands:read",
                        "categories:read");
        assertThat(roleByName(roles, "GERENTE").getPermissions())
                .extracting(Permission::getCode)
                .containsExactlyInAnyOrderElementsOf(MANAGER_PERMISSION_CODES);
    }

    private Role roleByName(List<Role> roles, String name) {
        return roles.stream()
                .filter(role -> name.equals(role.getName()))
                .findFirst()
                .orElseThrow();
    }

    private Permission permission(String code) {
        Permission permission = new Permission();
        permission.setId(UUID.randomUUID());
        permission.setCode(code);
        permission.setDescription(code);
        return permission;
    }
}
