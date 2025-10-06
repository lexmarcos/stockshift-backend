package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.auth.LoginRequest;
import com.stockshift.backend.api.dto.auth.LoginResponse;
import com.stockshift.backend.api.dto.auth.RefreshTokenRequest;
import com.stockshift.backend.domain.user.RefreshToken;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
import com.stockshift.backend.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private RefreshToken testRefreshToken;
    private LoginRequest loginRequest;
    private RefreshTokenRequest refreshTokenRequest;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword");
        testUser.setRole(UserRole.MANAGER);
        testUser.setActive(true);

        testRefreshToken = new RefreshToken();
        testRefreshToken.setId(UUID.randomUUID());
        testRefreshToken.setToken("test-refresh-token-12345");
        testRefreshToken.setUser(testUser);
        testRefreshToken.setExpiresAt(OffsetDateTime.now().plusDays(1));

        loginRequest = new LoginRequest("testuser", "password123");
        refreshTokenRequest = new RefreshTokenRequest("test-refresh-token-12345");

        authentication = new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());
    }

    @Test
    void shouldLoginSuccessfully() {
        // Given
        String accessToken = "test-access-token-12345";
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn(accessToken);
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn(testRefreshToken);

        // When
        LoginResponse response = authService.login(loginRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo(accessToken);
        assertThat(response.getRefreshToken()).isEqualTo(testRefreshToken.getToken());
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        assertThat(response.getUsername()).isEqualTo(testUser.getUsername());
        assertThat(response.getRole()).isEqualTo(UserRole.MANAGER.name());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtTokenProvider).generateAccessToken(testUser);
        verify(refreshTokenService).createRefreshToken(testUser);
    }

    @Test
    void shouldThrowExceptionWhenLoginWithInvalidCredentials() {
        // Given
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // When/Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void shouldRefreshTokenSuccessfully() {
        // Given
        String newAccessToken = "new-access-token-12345";
        when(refreshTokenService.findByToken(refreshTokenRequest.getRefreshToken()))
                .thenReturn(testRefreshToken);
        when(refreshTokenService.verifyExpiration(testRefreshToken)).thenReturn(testRefreshToken);
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn(newAccessToken);

        // When
        LoginResponse response = authService.refreshToken(refreshTokenRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo(newAccessToken);
        assertThat(response.getRefreshToken()).isEqualTo(testRefreshToken.getToken());
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        assertThat(response.getUsername()).isEqualTo(testUser.getUsername());
        assertThat(response.getRole()).isEqualTo(UserRole.MANAGER.name());

        verify(refreshTokenService).findByToken(refreshTokenRequest.getRefreshToken());
        verify(refreshTokenService).verifyExpiration(testRefreshToken);
        verify(jwtTokenProvider).generateAccessToken(testUser);
    }

    @Test
    void shouldThrowExceptionWhenRefreshTokenNotFound() {
        // Given
        when(refreshTokenService.findByToken(refreshTokenRequest.getRefreshToken()))
                .thenThrow(new RuntimeException("Refresh token not found"));

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(refreshTokenRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Refresh token not found");

        verify(refreshTokenService).findByToken(refreshTokenRequest.getRefreshToken());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void shouldThrowExceptionWhenRefreshTokenExpired() {
        // Given
        when(refreshTokenService.findByToken(refreshTokenRequest.getRefreshToken()))
                .thenReturn(testRefreshToken);
        doThrow(new RuntimeException("Refresh token expired"))
                .when(refreshTokenService).verifyExpiration(testRefreshToken);

        // When/Then
        assertThatThrownBy(() -> authService.refreshToken(refreshTokenRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Refresh token expired");

        verify(refreshTokenService).findByToken(refreshTokenRequest.getRefreshToken());
        verify(refreshTokenService).verifyExpiration(testRefreshToken);
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void shouldLogoutSuccessfully() {
        // Given
        String refreshToken = "test-refresh-token-12345";
        doNothing().when(refreshTokenService).revokeToken(refreshToken);

        // When
        authService.logout(refreshToken);

        // Then
        verify(refreshTokenService).revokeToken(refreshToken);
    }

    @Test
    void shouldHandleLogoutWithInvalidToken() {
        // Given
        String refreshToken = "invalid-token";
        doThrow(new RuntimeException("Token not found"))
                .when(refreshTokenService).revokeToken(refreshToken);

        // When/Then
        assertThatThrownBy(() -> authService.logout(refreshToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token not found");

        verify(refreshTokenService).revokeToken(refreshToken);
    }

    @Test
    void shouldLoginWithDifferentUserRoles() {
        // Given - Admin user
        User adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setUsername("adminuser");
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setActive(true);

        Authentication adminAuth = new UsernamePasswordAuthenticationToken(adminUser, null, adminUser.getAuthorities());
        String accessToken = "admin-access-token";

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(adminAuth);
        when(jwtTokenProvider.generateAccessToken(adminUser)).thenReturn(accessToken);
        when(refreshTokenService.createRefreshToken(adminUser)).thenReturn(testRefreshToken);

        // When
        LoginResponse response = authService.login(loginRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo(UserRole.ADMIN.name());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void shouldLoginWithSellerRole() {
        // Given - Seller user
        User sellerUser = new User();
        sellerUser.setId(UUID.randomUUID());
        sellerUser.setUsername("selleruser");
        sellerUser.setRole(UserRole.SELLER);
        sellerUser.setActive(true);

        Authentication sellerAuth = new UsernamePasswordAuthenticationToken(sellerUser, null, sellerUser.getAuthorities());
        String accessToken = "seller-access-token";

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(sellerAuth);
        when(jwtTokenProvider.generateAccessToken(sellerUser)).thenReturn(accessToken);
        when(refreshTokenService.createRefreshToken(sellerUser)).thenReturn(testRefreshToken);

        // When
        LoginResponse response = authService.login(loginRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo(UserRole.SELLER.name());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void shouldGenerateCorrectTokenTypeAndExpiryOnLogin() {
        // Given
        String accessToken = "test-token";
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn(accessToken);
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn(testRefreshToken);

        // When
        LoginResponse response = authService.login(loginRequest);

        // Then
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
    }

    @Test
    void shouldGenerateCorrectTokenTypeAndExpiryOnRefresh() {
        // Given
        String newAccessToken = "new-token";
        when(refreshTokenService.findByToken(refreshTokenRequest.getRefreshToken()))
                .thenReturn(testRefreshToken);
        when(refreshTokenService.verifyExpiration(testRefreshToken)).thenReturn(testRefreshToken);
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn(newAccessToken);

        // When
        LoginResponse response = authService.refreshToken(refreshTokenRequest);

        // Then
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
    }
}
