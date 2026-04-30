package br.com.stockshift.security;

import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.service.PermissionResolverService;
import br.com.stockshift.service.TokenDenylistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock
  private JwtTokenProvider tokenProvider;

  @Mock
  private CustomUserDetailsService userDetailsService;

  @Mock
  private TokenDenylistService tokenDenylistService;

  @Mock
  private PermissionResolverService permissionResolverService;

  @Mock
  private TenantRepository tenantRepository;

  @Mock
  private FilterChain filterChain;

  @InjectMocks
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private UserDetails userDetails;
  private UUID userId;
  private UUID tenantId;
  private UUID warehouseId;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    userId = UUID.randomUUID();
    tenantId = UUID.randomUUID();
    warehouseId = UUID.randomUUID();

    userDetails = new UserPrincipal(
        userId,
        tenantId,
        "user@test.com",
        "password",
        true,
        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
        Set.of(),
        false);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    WarehouseContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldAuthenticateWithCookie() throws ServletException, IOException {
    // Given
    String token = "valid-jwt-token";
    String jti = "test-jti";
    Cookie accessTokenCookie = new Cookie("accessToken", token);
    request.setCookies(accessTokenCookie);

    when(tokenProvider.validateToken(token)).thenReturn(true);
    when(tokenProvider.getJtiFromToken(token)).thenReturn(jti);
    when(tokenDenylistService.isDenylisted(jti)).thenReturn(false);
    when(tokenProvider.getUserIdFromToken(token)).thenReturn(userId);
    when(tokenProvider.getTenantIdFromToken(token)).thenReturn(tenantId);
    when(userDetailsService.loadUserById(userId.toString())).thenReturn(userDetails);
    when(tenantRepository.findById(tenantId)).thenReturn(java.util.Optional.of(activeTenant()));

    // When
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // Then
    verify(tokenProvider).validateToken(token);
    verify(tokenDenylistService).isDenylisted(jti);
    verify(userDetailsService).loadUserById(userId.toString());
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldAuthenticateWithAuthorizationHeader() throws ServletException, IOException {
    // Given
    String token = "valid-jwt-token";
    String jti = "test-jti";
    request.addHeader("Authorization", "Bearer " + token);

    when(tokenProvider.validateToken(token)).thenReturn(true);
    when(tokenProvider.getJtiFromToken(token)).thenReturn(jti);
    when(tokenDenylistService.isDenylisted(jti)).thenReturn(false);
    when(tokenProvider.getUserIdFromToken(token)).thenReturn(userId);
    when(tokenProvider.getTenantIdFromToken(token)).thenReturn(tenantId);
    when(userDetailsService.loadUserById(userId.toString())).thenReturn(userDetails);
    when(tenantRepository.findById(tenantId)).thenReturn(java.util.Optional.of(activeTenant()));

    // When
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // Then
    verify(tokenProvider).validateToken(token);
    verify(tokenDenylistService).isDenylisted(jti);
    verify(userDetailsService).loadUserById(userId.toString());
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldPrioritizeCookieOverHeader() throws ServletException, IOException {
    // Given
    String cookieToken = "cookie-token";
    String headerToken = "header-token";
    String jti = "test-jti";

    Cookie accessTokenCookie = new Cookie("accessToken", cookieToken);
    request.setCookies(accessTokenCookie);
    request.addHeader("Authorization", "Bearer " + headerToken);

    when(tokenProvider.validateToken(cookieToken)).thenReturn(true);
    when(tokenProvider.getJtiFromToken(cookieToken)).thenReturn(jti);
    when(tokenDenylistService.isDenylisted(jti)).thenReturn(false);
    when(tokenProvider.getUserIdFromToken(cookieToken)).thenReturn(userId);
    when(tokenProvider.getTenantIdFromToken(cookieToken)).thenReturn(tenantId);
    when(userDetailsService.loadUserById(userId.toString())).thenReturn(userDetails);
    when(tenantRepository.findById(tenantId)).thenReturn(java.util.Optional.of(activeTenant()));

    // When
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // Then
    verify(tokenProvider).validateToken(cookieToken);
    verify(tokenProvider, never()).validateToken(headerToken);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldContinueChainWhenNoTokenProvided() throws ServletException, IOException {
    // When
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // Then
    verify(tokenProvider, never()).validateToken(anyString());
    verify(userDetailsService, never()).loadUserById(anyString());
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void shouldClearContextsAfterAuthenticatedRequest() throws ServletException, IOException {
    String token = "valid-jwt-token";
    String jti = "test-jti";
    request.addHeader("Authorization", "Bearer " + token);

    when(tokenProvider.validateToken(token)).thenReturn(true);
    when(tokenProvider.getJtiFromToken(token)).thenReturn(jti);
    when(tokenDenylistService.isDenylisted(jti)).thenReturn(false);
    when(tokenProvider.getUserIdFromToken(token)).thenReturn(userId);
    when(tokenProvider.getTenantIdFromToken(token)).thenReturn(tenantId);
    when(tokenProvider.getWarehouseIdFromToken(token)).thenReturn(warehouseId);
    when(userDetailsService.loadUserById(userId.toString())).thenReturn(userDetails);
    when(tenantRepository.findById(tenantId)).thenReturn(java.util.Optional.of(activeTenant()));
    when(permissionResolverService.resolveUserRoleNames(userId, warehouseId)).thenReturn(Set.of("USER"));
    when(permissionResolverService.resolveUserPermissions(userId, warehouseId)).thenReturn(Set.of("users:read"));

    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    assertThat(TenantContext.getTenantId()).isNull();
    assertThat(WarehouseContext.getWarehouseId()).isNull();
  }

  @Test
  void shouldKeepAdminAuthoritiesWhenTokenHasWarehouseScope() throws ServletException, IOException {
    String token = "valid-jwt-token";
    String jti = "test-jti";
    UserDetails adminDetails = new UserPrincipal(
        userId,
        tenantId,
        "admin@test.com",
        "password",
        true,
        Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")),
        Set.of(),
        true);
    request.addHeader("Authorization", "Bearer " + token);

    when(tokenProvider.validateToken(token)).thenReturn(true);
    when(tokenProvider.getJtiFromToken(token)).thenReturn(jti);
    when(tokenDenylistService.isDenylisted(jti)).thenReturn(false);
    when(tokenProvider.getUserIdFromToken(token)).thenReturn(userId);
    when(tokenProvider.getTenantIdFromToken(token)).thenReturn(tenantId);
    when(tokenProvider.getWarehouseIdFromToken(token)).thenReturn(warehouseId);
    when(userDetailsService.loadUserById(userId.toString())).thenReturn(adminDetails);
    when(tenantRepository.findById(tenantId)).thenReturn(java.util.Optional.of(activeTenant()));

    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
        .extracting(authority -> authority.getAuthority())
        .contains("ROLE_ADMIN");
    verify(permissionResolverService, never()).resolveUserPermissions(userId, warehouseId);
    verify(permissionResolverService, never()).resolveUserRoleNames(userId, warehouseId);
  }

  @Test
  void shouldClearContextsEvenWhenNoToken() throws ServletException, IOException {
    TenantContext.setTenantId(UUID.randomUUID());
    WarehouseContext.setWarehouseId(UUID.randomUUID());

    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    assertThat(TenantContext.getTenantId()).isNull();
    assertThat(WarehouseContext.getWarehouseId()).isNull();
  }

  private Tenant activeTenant() {
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setBusinessName("Tenant");
    tenant.setEmail("tenant@test.com");
    tenant.setIsActive(true);
    return tenant;
  }
}
