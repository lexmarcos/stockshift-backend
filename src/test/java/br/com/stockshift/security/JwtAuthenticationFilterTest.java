package br.com.stockshift.security;

import br.com.stockshift.service.TokenDenylistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

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
  private FilterChain filterChain;

  @InjectMocks
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private UserDetails userDetails;
  private UUID userId;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    userId = UUID.randomUUID();
    tenantId = UUID.randomUUID();

    userDetails = new User(
        userId.toString(),
        "password",
        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
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
}
