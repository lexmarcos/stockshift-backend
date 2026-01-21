package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.auth.LoginRequest;
import br.com.stockshift.dto.auth.LoginResponse;
import br.com.stockshift.dto.auth.RefreshTokenRequest;
import br.com.stockshift.dto.auth.RefreshTokenResponse;
import br.com.stockshift.dto.auth.RegisterRequest;
import br.com.stockshift.dto.auth.RegisterResponse;
import br.com.stockshift.service.AuthService;
import br.com.stockshift.service.TenantService;
import br.com.stockshift.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication endpoints")
public class AuthController {

    private final AuthService authService;
    private final TenantService tenantService;
    private final CookieUtil cookieUtil;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and set HTTP-only cookies")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(request);

        // Add tokens to HTTP-only cookies
        cookieUtil.addAccessTokenCookie(response, loginResponse.getAccessToken());
        cookieUtil.addRefreshTokenCookie(response, loginResponse.getRefreshToken());

        // Remove tokens from JSON response for security
        loginResponse.setAccessToken(null);
        loginResponse.setRefreshToken(null);

        return ResponseEntity.ok(ApiResponse.success(loginResponse));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh Token", description = "Generate new tokens from refresh token cookie")
    public ResponseEntity<ApiResponse<Void>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        // Read refresh token from cookie
        String refreshTokenValue = cookieUtil.getRefreshTokenFromCookie(request.getCookies());

        if (refreshTokenValue == null) {
            throw new br.com.stockshift.exception.UnauthorizedException("Refresh token not found");
        }

        // Get new tokens with rotation
        RefreshTokenResponse tokens = authService.refresh(refreshTokenValue);

        // Set new cookies
        cookieUtil.addAccessTokenCookie(response, tokens.getAccessToken());
        cookieUtil.addRefreshTokenCookie(response, tokens.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.success("Tokens refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoke tokens and clear cookies")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        // Read tokens from cookies
        String accessToken = cookieUtil.getAccessTokenFromCookie(request.getCookies());
        String refreshTokenValue = cookieUtil.getRefreshTokenFromCookie(request.getCookies());

        // Revoke tokens
        authService.logout(accessToken, refreshTokenValue);

        // Remove cookies
        cookieUtil.removeAccessTokenCookie(response);
        cookieUtil.removeRefreshTokenCookie(response);

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    @PostMapping("/register")
    @Operation(summary = "Register", description = "Register new tenant with first admin user")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = tenantService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }
}
