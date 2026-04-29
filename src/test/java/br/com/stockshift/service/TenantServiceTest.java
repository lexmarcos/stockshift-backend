package br.com.stockshift.service;

import br.com.stockshift.config.JwtProperties;
import br.com.stockshift.dto.auth.RegisterRequest;
import br.com.stockshift.dto.auth.RegisterResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.RoleRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.JwtTokenProvider;
import br.com.stockshift.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
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
        service = new TenantService(tenantRepository, userRepository, roleRepository, passwordEncoder,
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
}
