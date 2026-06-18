package br.com.stockshift.service;

import br.com.stockshift.config.JwtProperties;
import br.com.stockshift.dto.auth.ChangePasswordRequest;
import br.com.stockshift.dto.auth.LoginRequest;
import br.com.stockshift.dto.auth.RefreshTokenResponse;
import br.com.stockshift.dto.auth.SwitchWarehouseRequest;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.UserRoleWarehouseRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.JwtTokenProvider;
import br.com.stockshift.security.UserPrincipal;
import br.com.stockshift.security.WarehouseContext;
import br.com.stockshift.service.audit.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private TokenDenylistService tokenDenylistService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private UserRoleWarehouseRepository userRoleWarehouseRepository;
    @Mock
    private PermissionResolverService permissionResolverService;
    @Mock
    private WarehouseAccessService warehouseAccessService;
    @Mock
    private AuditService auditService;

    private AuthService service;
    private JwtProperties jwtProperties;
    private UUID tenantId;
    private UUID userId;
    private UUID warehouseId;
    private User user;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        jwtProperties = new JwtProperties();
        jwtProperties.setAccessExpiration(60_000L);
        service = new AuthService(authenticationManager, userRepository, jwtTokenProvider, refreshTokenService,
                jwtProperties, tokenDenylistService, passwordEncoder, warehouseRepository,
                userRoleWarehouseRepository, permissionResolverService, warehouseAccessService, auditService);
        user = user("Seller", true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any(), anyList(), anyList()))
                .thenReturn("access");
        when(refreshTokenService.createRefreshToken(any(User.class), any())).thenReturn(refreshToken("refresh", warehouseId));
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn(refreshToken("refresh", null));
        when(refreshTokenService.rotateRefreshToken(any(RefreshToken.class), any()))
                .thenReturn(refreshToken("refresh", warehouseId));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        WarehouseContext.clear();
    }

    @Test
    void loginShouldAuthenticateResolveWarehousePermissionsAndAudit() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRoleWarehouseRepository.findWarehouseIdsByUserId(userId)).thenReturn(Set.of(warehouseId));
        when(permissionResolverService.resolveUserRoleNames(userId, warehouseId)).thenReturn(Set.of("Seller"));
        when(permissionResolverService.resolveUserPermissions(userId, warehouseId)).thenReturn(Set.of("sales:read"));

        var response = service.login(new LoginRequest("user@example.com", "password", null));

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(response.getExpiresIn()).isEqualTo(60_000L);
        assertThat(user.getLastLogin()).isNotNull();
        verify(auditService).record(any());

        user.setIsActive(false);
        assertThatThrownBy(() -> service.login(new LoginRequest("user@example.com", "password", null)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void loginShouldRecordBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("user@example.com", "bad", null)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");
        verify(auditService).record(any());
    }

    @Test
    void refreshShouldRotateTokensAndRejectInvalidUserStateOrWarehouseAccess() {
        RefreshToken refreshToken = refreshToken("old", warehouseId);
        refreshToken.setUser(user);
        when(refreshTokenService.validateRefreshToken("old")).thenReturn(refreshToken);
        when(warehouseAccessService.canAccessWarehouse(userId, warehouseId)).thenReturn(true);
        when(permissionResolverService.resolveUserRoleNames(userId, warehouseId)).thenReturn(Set.of("Seller"));
        when(permissionResolverService.resolveUserPermissions(userId, warehouseId)).thenReturn(Set.of("sales:read"));

        RefreshTokenResponse response = service.refresh("old");

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        verify(auditService).record(any());

        refreshToken.setUser(null);
        assertThatThrownBy(() -> service.refresh("old")).isInstanceOf(UnauthorizedException.class);

        refreshToken.setUser(user);
        user.setIsActive(false);
        assertThatThrownBy(() -> service.refresh("old")).isInstanceOf(UnauthorizedException.class);

        user.setIsActive(true);
        when(warehouseAccessService.canAccessWarehouse(userId, warehouseId)).thenReturn(false);
        assertThatThrownBy(() -> service.refresh("old")).isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("no longer has access");
    }

    @Test
    void logoutShouldRevokeAuthenticatedUserTokensDenylistAccessTokenAndAudit() {
        setPrincipal();
        when(jwtTokenProvider.getJtiFromToken("access")).thenReturn("jti");
        when(jwtTokenProvider.getRemainingTtl("access")).thenReturn(1000L);

        service.logout("access");

        // Revokes the AUTHENTICATED user's whole session (security context), never a
        // user inferred from the request-supplied refresh cookie.
        verify(refreshTokenService).revokeAllUserTokens(userId);
        verify(tokenDenylistService).addToDenylist("jti", 1000L);
        verify(auditService, atLeastOnce()).record(any());
    }

    @Test
    void changePasswordAndGetMeShouldUseSecurityContext() {
        setPrincipal();
        WarehouseContext.setWarehouseId(warehouseId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "encoded")).thenReturn(true);
        when(passwordEncoder.matches("wrong", "new-encoded")).thenReturn(false);
        when(passwordEncoder.encode("newpass")).thenReturn("new-encoded");
        when(permissionResolverService.resolveUserRoleNames(userId, warehouseId)).thenReturn(Set.of());
        when(permissionResolverService.resolveUserPermissions(userId, warehouseId)).thenReturn(Set.of("sales:read"));

        service.changePassword(new ChangePasswordRequest("old", "newpass"));

        assertThat(user.getPassword()).isEqualTo("new-encoded");
        assertThat(user.getMustChangePassword()).isFalse();
        assertThat(service.getMe().getPermissions()).containsExactly("sales:read");

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("wrong", "newpass")))
                .isInstanceOf(BusinessException.class);

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.getMe()).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void switchWarehouseShouldAllowAssignedOrAdminWarehouseAndRejectMissingAccess() {
        setPrincipal();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(warehouseAccessService.canAccessWarehouse(userId, warehouseId)).thenReturn(true);
        when(permissionResolverService.resolveUserRoleNames(userId, warehouseId)).thenReturn(Set.of("Seller"));
        when(permissionResolverService.resolveUserPermissions(userId, warehouseId)).thenReturn(Set.of("sales:read"));

        assertThat(service.switchWarehouse(new SwitchWarehouseRequest(warehouseId)).getAccessToken()).isEqualTo("access");

        UUID otherWarehouseId = UUID.randomUUID();
        when(warehouseAccessService.canAccessWarehouse(userId, otherWarehouseId)).thenReturn(false);
        assertThatThrownBy(() -> service.switchWarehouse(new SwitchWarehouseRequest(otherWarehouseId)))
                .isInstanceOf(ForbiddenException.class);

        user.getRoles().clear();
        user.getRoles().add(role("ADMIN"));
        when(warehouseRepository.findByTenantIdAndId(tenantId, otherWarehouseId))
                .thenReturn(Optional.of(warehouse(otherWarehouseId)));
        assertThat(service.switchWarehouse(new SwitchWarehouseRequest(otherWarehouseId)).getRefreshToken())
                .isEqualTo("refresh");
    }

    private void setPrincipal() {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "password", principal.getAuthorities()));
    }

    private User user(String roleName, boolean active) {
        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setEmail("user@example.com");
        user.setPassword("encoded");
        user.setFullName("User");
        user.setIsActive(active);
        user.setMustChangePassword(true);
        user.setRoles(new java.util.HashSet<>(Set.of(role(roleName))));
        user.setWarehouses(new java.util.HashSet<>(Set.of(warehouse(warehouseId))));
        return user;
    }

    private Role role(String name) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setTenantId(tenantId);
        role.setName(name);
        role.setPermissions(Set.of());
        return role;
    }

    private Warehouse warehouse(UUID id) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setTenantId(tenantId);
        warehouse.setName("Warehouse");
        return warehouse;
    }

    private RefreshToken refreshToken(String token, UUID selectedWarehouseId) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setToken(token);
        refreshToken.setUser(user);
        refreshToken.setWarehouseId(selectedWarehouseId);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        return refreshToken;
    }
}
