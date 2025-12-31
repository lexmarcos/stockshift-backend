# HTTP-Only Tokens Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate JWT authentication from JSON body to HTTP-only cookies with token rotation for enhanced security.

**Architecture:** Tokens stored in HTTP-only cookies (not accessible via JavaScript), dual reading strategy (cookies first, Authorization header fallback), refresh token rotation on each refresh, environment-specific cookie configuration.

**Tech Stack:** Spring Boot, Spring Security, JWT, HTTP Cookies, Jakarta Servlet API

---

## Task 1: Create Cookie Configuration Classes

**Files:**
- Create: `src/main/java/br/com/stockshift/config/CookieProperties.java`
- Create: `src/main/java/br/com/stockshift/util/CookieUtil.java`

**Step 1: Create CookieProperties configuration class**

Create file with complete code:

```java
package br.com.stockshift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt.cookie")
@Data
public class CookieProperties {
    private boolean secure = false;
    private String sameSite = "Lax";
    private String domain;
    private String path = "/api";
    private boolean httpOnly = true;
}
```

**Step 2: Create CookieUtil helper class**

Create file with complete code:

```java
package br.com.stockshift.util;

import br.com.stockshift.config.CookieProperties;
import br.com.stockshift.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class CookieUtil {
    
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    
    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;
    
    public void addAccessTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(cookieProperties.isHttpOnly())
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .maxAge(Duration.ofMillis(jwtProperties.getAccessExpiration()))
                .sameSite(cookieProperties.getSameSite())
                .build();
        
        if (cookieProperties.getDomain() != null && !cookieProperties.getDomain().isEmpty()) {
            cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                    .httpOnly(cookieProperties.isHttpOnly())
                    .secure(cookieProperties.isSecure())
                    .path(cookieProperties.getPath())
                    .maxAge(Duration.ofMillis(jwtProperties.getAccessExpiration()))
                    .sameSite(cookieProperties.getSameSite())
                    .domain(cookieProperties.getDomain())
                    .build();
        }
        
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    public void addRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(cookieProperties.isHttpOnly())
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .maxAge(Duration.ofMillis(jwtProperties.getRefreshExpiration()))
                .sameSite(cookieProperties.getSameSite())
                .build();
        
        if (cookieProperties.getDomain() != null && !cookieProperties.getDomain().isEmpty()) {
            cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                    .httpOnly(cookieProperties.isHttpOnly())
                    .secure(cookieProperties.isSecure())
                    .path(cookieProperties.getPath())
                    .maxAge(Duration.ofMillis(jwtProperties.getRefreshExpiration()))
                    .sameSite(cookieProperties.getSameSite())
                    .domain(cookieProperties.getDomain())
                    .build();
        }
        
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    public void removeAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .maxAge(0)
                .sameSite(cookieProperties.getSameSite())
                .build();
        
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    public void removeRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .maxAge(0)
                .sameSite(cookieProperties.getSameSite())
                .build();
        
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    public String getRefreshTokenFromCookie(Cookie[] cookies) {
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
```

**Step 3: Update application.yml with cookie properties**

Add to `src/main/resources/application.yml` under jwt section:

```yaml
jwt:
  secret: ${JWT_SECRET:dev-secret-key-change-in-production-must-be-at-least-256-bits-long}
  access-expiration: ${JWT_ACCESS_EXPIRATION:900000}
  refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000}
  cookie:
    secure: ${JWT_COOKIE_SECURE:false}
    same-site: ${JWT_COOKIE_SAME_SITE:Lax}
    domain: ${JWT_COOKIE_DOMAIN:}
    path: /api
    http-only: true
```

**Step 4: Build to verify no compilation errors**

Run: `./gradlew clean build -x test`

Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/config/CookieProperties.java
git add src/main/java/br/com/stockshift/util/CookieUtil.java
git add src/main/resources/application.yml
git commit -m "feat: add cookie configuration and utility classes"
```

---

## Task 2: Update JwtAuthenticationFilter for Dual Token Reading

**Files:**
- Modify: `src/main/java/br/com/stockshift/security/JwtAuthenticationFilter.java`

**Step 1: Add cookie reading logic to getJwtFromRequest method**

Replace the `getJwtFromRequest` method:

```java
private String getJwtFromRequest(HttpServletRequest request) {
    // Priority 1: Read from accessToken cookie
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
        for (Cookie cookie : cookies) {
            if ("accessToken".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
    }
    
    // Priority 2: Fallback to Authorization header
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
        return bearerToken.substring(7);
    }
    
    return null;
}
```

**Step 2: Add Cookie import**

Add to imports section:

```java
import jakarta.servlet.http.Cookie;
```

**Step 3: Build to verify no compilation errors**

Run: `./gradlew clean build -x test`

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/security/JwtAuthenticationFilter.java
git commit -m "feat: add cookie-based JWT token reading with header fallback"
```

---

## Task 3: Update AuthService with Token Rotation

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/AuthService.java`
- Modify: `src/main/java/br/com/stockshift/dto/auth/RefreshTokenResponse.java`

**Step 1: Update RefreshTokenResponse to include new refresh token**

Modify `src/main/java/br/com/stockshift/dto/auth/RefreshTokenResponse.java`:

```java
package br.com.stockshift.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenResponse {
    private String accessToken;
    private String refreshToken;  // Add this field for new refresh token
    private String tokenType;
    private Long expiresIn;
}
```

**Step 2: Update AuthService refresh method with token rotation**

Replace the `refresh` method in `AuthService`:

```java
@Transactional
public RefreshTokenResponse refresh(String refreshTokenValue) {
    // Validate refresh token
    RefreshToken refreshToken = refreshTokenService.validateRefreshToken(refreshTokenValue);

    // Load user
    User user = refreshToken.getUser();
    if (user == null) {
        throw new UnauthorizedException("User not found");
    }

    if (!user.getIsActive()) {
        throw new UnauthorizedException("User account is disabled");
    }

    // Generate new access token
    String accessToken = jwtTokenProvider.generateAccessToken(
            user.getId(),
            user.getTenantId(),
            user.getEmail());

    // Rotate refresh token - create new one (this deletes the old one)
    RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user);

    return RefreshTokenResponse.builder()
            .accessToken(accessToken)
            .refreshToken(newRefreshToken.getToken())
            .tokenType("Bearer")
            .expiresIn(jwtProperties.getAccessExpiration())
            .build();
}
```

**Step 3: Build to verify no compilation errors**

Run: `./gradlew clean build -x test`

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/auth/RefreshTokenResponse.java
git add src/main/java/br/com/stockshift/service/AuthService.java
git commit -m "feat: implement refresh token rotation in AuthService"
```

---

## Task 4: Update AuthController Endpoints

**Files:**
- Modify: `src/main/java/br/com/stockshift/controller/AuthController.java`

**Step 1: Add CookieUtil injection and imports**

Add to class fields:

```java
private final CookieUtil cookieUtil;
```

Add to imports:

```java
import br.com.stockshift.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
```

**Step 2: Update login endpoint to use cookies**

Replace the `login` method:

```java
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
```

**Step 3: Update refresh endpoint to read from cookie**

Replace the `refresh` method:

```java
@PostMapping("/refresh")
@Operation(summary = "Refresh Token", description = "Generate new tokens from refresh token cookie")
public ResponseEntity<ApiResponse<Void>> refresh(
        HttpServletRequest request,
        HttpServletResponse response) {
    
    // Read refresh token from cookie
    String refreshTokenValue = cookieUtil.getRefreshTokenFromCookie(request.getCookies());
    
    if (refreshTokenValue == null) {
        throw new UnauthorizedException("Refresh token not found");
    }
    
    // Get new tokens with rotation
    RefreshTokenResponse tokens = authService.refresh(refreshTokenValue);
    
    // Set new cookies
    cookieUtil.addAccessTokenCookie(response, tokens.getAccessToken());
    cookieUtil.addRefreshTokenCookie(response, tokens.getRefreshToken());
    
    return ResponseEntity.ok(ApiResponse.success("Tokens refreshed successfully"));
}
```

**Step 4: Update logout endpoint to clear cookies**

Replace the `logout` method:

```java
@PostMapping("/logout")
@Operation(summary = "Logout", description = "Revoke refresh token and clear cookies")
public ResponseEntity<ApiResponse<Void>> logout(
        HttpServletRequest request,
        HttpServletResponse response) {
    
    // Read refresh token from cookie
    String refreshTokenValue = cookieUtil.getRefreshTokenFromCookie(request.getCookies());
    
    if (refreshTokenValue != null) {
        authService.logout(refreshTokenValue);
    }
    
    // Remove cookies
    cookieUtil.removeAccessTokenCookie(response);
    cookieUtil.removeRefreshTokenCookie(response);
    
    return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
}
```

**Step 5: Build to verify no compilation errors**

Run: `./gradlew clean build -x test`

Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/AuthController.java
git commit -m "feat: update auth endpoints to use HTTP-only cookies"
```

---

## Task 5: Update SecurityConfig for CORS with Credentials

**Files:**
- Modify: `src/main/java/br/com/stockshift/config/SecurityConfig.java`

**Step 1: Read current CORS configuration**

Run: Check if CorsConfigurationSource bean exists and needs updating

**Step 2: Update CORS to allow credentials**

Find the `corsConfigurationSource` method and ensure it has:

```java
configuration.setAllowCredentials(true);
```

If the method doesn't exist or needs update, it should look like:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource(
        @Value("${cors.allowed-origins}") String allowedOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);  // ESSENTIAL for cookies
    configuration.setMaxAge(3600L);
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
}
```

**Step 3: Build to verify no compilation errors**

Run: `./gradlew clean build -x test`

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/config/SecurityConfig.java
git commit -m "feat: enable CORS credentials for HTTP-only cookies"
```

---

## Task 6: Update Integration Tests

**Files:**
- Modify: `src/test/java/br/com/stockshift/controller/AuthenticationControllerIntegrationTest.java`

**Step 1: Update shouldLoginSuccessfully test to check cookies**

Replace the test:

```java
@Test
void shouldLoginSuccessfully() throws Exception {
    LoginRequest request = new LoginRequest();
    request.setEmail("auth@test.com");
    request.setPassword("password123");

    MvcResult result = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("auth@test.com"))
            .andExpect(jsonPath("$.data.accessToken").doesNotExist())  // Should not be in JSON
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist()) // Should not be in JSON
            .andExpect(cookie().exists("accessToken"))
            .andExpect(cookie().exists("refreshToken"))
            .andExpect(cookie().httpOnly("accessToken", true))
            .andExpect(cookie().httpOnly("refreshToken", true))
            .andExpect(cookie().path("accessToken", "/api"))
            .andExpect(cookie().path("refreshToken", "/api"))
            .andReturn();
}
```

**Step 2: Update shouldRefreshToken test to use cookies**

Replace the test:

```java
@Test
void shouldRefreshToken() throws Exception {
    // First, login to get cookies
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail("auth@test.com");
    loginRequest.setPassword("password123");

    MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginRequest)))
            .andReturn();

    Cookie accessTokenCookie = loginResult.getResponse().getCookie("accessToken");
    Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");

    assertNotNull(refreshTokenCookie);

    // Now test refresh using cookies
    mockMvc.perform(post("/api/auth/refresh")
            .cookie(refreshTokenCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(cookie().exists("accessToken"))
            .andExpect(cookie().exists("refreshToken"));
}
```

**Step 3: Update shouldLogoutSuccessfully test to use cookies**

Replace the test:

```java
@Test
void shouldLogoutSuccessfully() throws Exception {
    // First, login to get cookies
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail("auth@test.com");
    loginRequest.setPassword("password123");

    MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginRequest)))
            .andReturn();

    Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");
    assertNotNull(refreshTokenCookie);

    // Now test logout
    MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
            .cookie(refreshTokenCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();

    // Check that cookies are cleared (Max-Age=0)
    Cookie clearedAccessToken = logoutResult.getResponse().getCookie("accessToken");
    Cookie clearedRefreshToken = logoutResult.getResponse().getCookie("refreshToken");
    
    assertNotNull(clearedAccessToken);
    assertNotNull(clearedRefreshToken);
    assertEquals(0, clearedAccessToken.getMaxAge());
    assertEquals(0, clearedRefreshToken.getMaxAge());
}
```

**Step 4: Add import for Cookie**

Add to imports:

```java
import jakarta.servlet.http.Cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.junit.jupiter.api.Assertions.*;
```

**Step 5: Run the integration tests**

Run: `./gradlew test --tests AuthenticationControllerIntegrationTest`

Expected: All 3 tests PASS

**Step 6: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/AuthenticationControllerIntegrationTest.java
git commit -m "test: update auth integration tests for HTTP-only cookies"
```

---

## Task 7: Add Test for Dual Token Reading (Cookie vs Header)

**Files:**
- Create: `src/test/java/br/com/stockshift/security/JwtAuthenticationFilterTest.java`

**Step 1: Create JwtAuthenticationFilter unit test**

Create test file with complete code:

```java
package br.com.stockshift.security;

import br.com.stockshift.service.CustomUserDetailsService;
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
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    void shouldAuthenticateWithCookie() throws ServletException, IOException {
        // Given
        String token = "valid-jwt-token";
        Cookie accessTokenCookie = new Cookie("accessToken", token);
        request.setCookies(accessTokenCookie);

        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(token)).thenReturn(userId);
        when(tokenProvider.getTenantIdFromToken(token)).thenReturn(tenantId);
        when(userDetailsService.loadUserById(userId.toString())).thenReturn(userDetails);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(tokenProvider).validateToken(token);
        verify(userDetailsService).loadUserById(userId.toString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAuthenticateWithAuthorizationHeader() throws ServletException, IOException {
        // Given
        String token = "valid-jwt-token";
        request.addHeader("Authorization", "Bearer " + token);

        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(token)).thenReturn(userId);
        when(tokenProvider.getTenantIdFromToken(token)).thenReturn(tenantId);
        when(userDetailsService.loadUserById(userId.toString())).thenReturn(userDetails);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(tokenProvider).validateToken(token);
        verify(userDetailsService).loadUserById(userId.toString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPrioritizeCookieOverHeader() throws ServletException, IOException {
        // Given
        String cookieToken = "cookie-token";
        String headerToken = "header-token";
        
        Cookie accessTokenCookie = new Cookie("accessToken", cookieToken);
        request.setCookies(accessTokenCookie);
        request.addHeader("Authorization", "Bearer " + headerToken);

        when(tokenProvider.validateToken(cookieToken)).thenReturn(true);
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
```

**Step 2: Run the new unit tests**

Run: `./gradlew test --tests JwtAuthenticationFilterTest`

Expected: All 4 tests PASS

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/security/JwtAuthenticationFilterTest.java
git commit -m "test: add JWT filter tests for dual token reading"
```

---

## Task 8: Run Full Test Suite and Verification

**Step 1: Run all tests**

Run: `./gradlew test`

Expected: All tests PASS

**Step 2: Check for any compilation warnings**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL with no warnings

**Step 3: Verify application starts successfully**

Run: `./gradlew bootRun`

Expected: Application starts without errors, check logs for:
- Cookie properties loaded correctly
- No bean creation errors
- Security filter chain configured

**Step 4: Manual test with curl (optional)**

Test login:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password"}' \
  -c cookies.txt -v
```

Expected: See Set-Cookie headers for accessToken and refreshToken

Test refresh:
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -b cookies.txt -v
```

Expected: New Set-Cookie headers with rotated tokens

**Step 5: Final commit and summary**

```bash
git add .
git commit -m "feat: complete HTTP-only cookie authentication implementation"
```

---

## Summary of Changes

**Created Files:**
- `CookieProperties.java` - Cookie configuration from application.yml
- `CookieUtil.java` - Helper methods for cookie management
- `JwtAuthenticationFilterTest.java` - Unit tests for dual reading

**Modified Files:**
- `application.yml` - Added cookie configuration properties
- `JwtAuthenticationFilter.java` - Added cookie reading with header fallback
- `AuthService.java` - Updated refresh method with token rotation
- `AuthController.java` - Updated all endpoints to use cookies
- `RefreshTokenResponse.java` - Added refreshToken field for rotation
- `SecurityConfig.java` - Enabled CORS credentials
- `AuthenticationControllerIntegrationTest.java` - Updated tests for cookies

**Security Improvements:**
- ✅ Tokens in HTTP-only cookies (XSS protection)
- ✅ Refresh token rotation (replay attack protection)
- ✅ Environment-specific cookie configuration
- ✅ CORS with credentials enabled
- ✅ Dual reading (backward compatible with testing tools)

**Testing Coverage:**
- ✅ Login returns cookies, not JSON tokens
- ✅ Refresh reads from cookie and rotates tokens
- ✅ Logout clears cookies
- ✅ Filter reads from cookie first, then header
- ✅ Cookie takes priority over header when both present

