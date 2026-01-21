# JWT Token Denylist Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implementar invalidação de access tokens JWT no logout usando Redis como denylist.

**Architecture:** Adicionar `jti` claim aos JWTs, armazenar tokens revogados no Redis com TTL automático, verificar denylist no filtro de autenticação antes de aceitar tokens.

**Tech Stack:** Spring Boot 4.0.1, Spring Data Redis, Redis

---

## Task 1: Adicionar Dependência Redis

**Files:**
- Modify: `build.gradle:27-38`

**Step 1: Adicionar dependência Spring Data Redis**

Adicionar após a linha 38 (após as dependências JWT):

```gradle
	// Redis for token denylist
	implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

**Step 2: Verificar que o Gradle resolve a dependência**

Run: `./gradlew dependencies --configuration compileClasspath | grep redis`
Expected: `org.springframework.boot:spring-boot-starter-data-redis`

**Step 3: Commit**

```bash
git add build.gradle
git commit -m "build: add spring-boot-starter-data-redis dependency"
```

---

## Task 2: Configurar Redis

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-test.yml`

**Step 1: Adicionar configuração Redis ao application.yml**

Adicionar após a seção `spring.flyway` (linha 25):

```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
```

**Step 2: Adicionar configuração Redis para testes**

Adicionar ao `application-test.yml`:

```yaml
  data:
    redis:
      host: localhost
      port: 6379
```

**Step 3: Commit**

```bash
git add src/main/resources/application.yml src/main/resources/application-test.yml
git commit -m "config: add Redis configuration for token denylist"
```

---

## Task 3: Criar RedisConfig

**Files:**
- Create: `src/main/java/br/com/stockshift/config/RedisConfig.java`

**Step 1: Criar classe de configuração Redis**

```java
package br.com.stockshift.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/config/RedisConfig.java
git commit -m "feat: add Redis configuration bean"
```

---

## Task 4: Criar TokenDenylistService Interface

**Files:**
- Create: `src/main/java/br/com/stockshift/service/TokenDenylistService.java`

**Step 1: Criar interface**

```java
package br.com.stockshift.service;

/**
 * Service for managing JWT token denylist.
 * Used to invalidate access tokens on logout.
 */
public interface TokenDenylistService {

    /**
     * Add a token to the denylist.
     *
     * @param jti JWT ID to blacklist
     * @param ttlMillis time-to-live in milliseconds (should match token remaining lifetime)
     */
    void addToDenylist(String jti, long ttlMillis);

    /**
     * Check if a token is in the denylist.
     *
     * @param jti JWT ID to check
     * @return true if token is denylisted (revoked), false otherwise
     */
    boolean isDenylisted(String jti);
}
```

**Step 2: Commit**

```bash
git add src/main/java/br/com/stockshift/service/TokenDenylistService.java
git commit -m "feat: add TokenDenylistService interface"
```

---

## Task 5: Implementar RedisTokenDenylistService

**Files:**
- Create: `src/main/java/br/com/stockshift/service/RedisTokenDenylistService.java`
- Create: `src/test/java/br/com/stockshift/service/RedisTokenDenylistServiceTest.java`

**Step 1: Escrever teste unitário**

```java
package br.com.stockshift.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisTokenDenylistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisTokenDenylistService denylistService;

    @BeforeEach
    void setUp() {
        denylistService = new RedisTokenDenylistService(redisTemplate);
    }

    @Test
    void addToDenylist_shouldStoreJtiWithTtl() {
        // Given
        String jti = "test-jti-123";
        long ttlMillis = 60000L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        denylistService.addToDenylist(jti, ttlMillis);

        // Then
        verify(valueOperations).set(
                eq("token:denylist:test-jti-123"),
                eq("1"),
                eq(60000L),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void isDenylisted_whenJtiExists_shouldReturnTrue() {
        // Given
        String jti = "revoked-jti";
        when(redisTemplate.hasKey("token:denylist:revoked-jti")).thenReturn(true);

        // When
        boolean result = denylistService.isDenylisted(jti);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isDenylisted_whenJtiDoesNotExist_shouldReturnFalse() {
        // Given
        String jti = "valid-jti";
        when(redisTemplate.hasKey("token:denylist:valid-jti")).thenReturn(false);

        // When
        boolean result = denylistService.isDenylisted(jti);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isDenylisted_whenRedisThrowsException_shouldReturnFalse() {
        // Given (fail-open strategy)
        String jti = "any-jti";
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        // When
        boolean result = denylistService.isDenylisted(jti);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void addToDenylist_whenRedisThrowsException_shouldNotThrow() {
        // Given (fail-open strategy)
        String jti = "test-jti";
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis unavailable"));

        // When/Then - should not throw
        denylistService.addToDenylist(jti, 60000L);
    }
}
```

**Step 2: Executar teste para verificar que falha**

Run: `./gradlew test --tests RedisTokenDenylistServiceTest`
Expected: FAIL - class not found

**Step 3: Implementar RedisTokenDenylistService**

```java
package br.com.stockshift.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisTokenDenylistService implements TokenDenylistService {

    private static final String DENYLIST_KEY_PREFIX = "token:denylist:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addToDenylist(String jti, long ttlMillis) {
        try {
            String key = DENYLIST_KEY_PREFIX + jti;
            redisTemplate.opsForValue().set(key, "1", ttlMillis, TimeUnit.MILLISECONDS);
            log.debug("Added token to denylist: {}", jti);
        } catch (Exception e) {
            // Fail-open: log error but don't block logout
            log.error("Failed to add token to denylist: {}. Error: {}", jti, e.getMessage());
        }
    }

    @Override
    public boolean isDenylisted(String jti) {
        try {
            String key = DENYLIST_KEY_PREFIX + jti;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            // Fail-open: if Redis is unavailable, allow token (prioritize availability)
            log.warn("Failed to check token denylist: {}. Allowing token (fail-open). Error: {}", jti, e.getMessage());
            return false;
        }
    }
}
```

**Step 4: Executar teste para verificar que passa**

Run: `./gradlew test --tests RedisTokenDenylistServiceTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/RedisTokenDenylistService.java src/test/java/br/com/stockshift/service/RedisTokenDenylistServiceTest.java
git commit -m "feat: implement RedisTokenDenylistService with fail-open strategy"
```

---

## Task 6: Adicionar JTI ao JWT

**Files:**
- Modify: `src/main/java/br/com/stockshift/security/JwtTokenProvider.java`

**Step 1: Modificar generateAccessToken para incluir jti**

Substituir o método `generateAccessToken`:

```java
public String generateAccessToken(UUID userId, UUID tenantId, String email) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessExpiration());
    String jti = UUID.randomUUID().toString();

    return Jwts.builder()
            .id(jti)
            .subject(userId.toString())
            .claim("tenantId", tenantId.toString())
            .claim("email", email)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getSigningKey(), Jwts.SIG.HS256)
            .compact();
}
```

**Step 2: Adicionar método getJtiFromToken**

```java
public String getJtiFromToken(String token) {
    Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();

    return claims.getId();
}
```

**Step 3: Adicionar método getRemainingTtl**

```java
public long getRemainingTtl(String token) {
    Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();

    Date expiration = claims.getExpiration();
    long remaining = expiration.getTime() - System.currentTimeMillis();
    return Math.max(remaining, 0);
}
```

**Step 4: Executar testes existentes**

Run: `./gradlew test --tests "*Jwt*"`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/security/JwtTokenProvider.java
git commit -m "feat: add jti claim and extraction methods to JwtTokenProvider"
```

---

## Task 7: Verificar Denylist no Filtro de Autenticação

**Files:**
- Modify: `src/main/java/br/com/stockshift/security/JwtAuthenticationFilter.java`

**Step 1: Adicionar dependência do TokenDenylistService**

Modificar a classe para injetar o serviço:

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenDenylistService tokenDenylistService;
```

**Step 2: Modificar doFilterInternal para verificar denylist**

Substituir o bloco de validação do token:

```java
@Override
protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
    try {
        String jwt = getJwtFromRequest(request);

        if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
            // Check if token is revoked
            String jti = tokenProvider.getJtiFromToken(jwt);
            if (tokenDenylistService.isDenylisted(jti)) {
                log.warn("Attempted use of revoked token: {}", jti);
                filterChain.doFilter(request, response);
                return;
            }

            UUID userId = tokenProvider.getUserIdFromToken(jwt);
            UUID tenantId = tokenProvider.getTenantIdFromToken(jwt);

            // Set tenant context
            TenantContext.setTenantId(tenantId);

            UserDetails userDetails = userDetailsService.loadUserById(userId.toString());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    } catch (Exception ex) {
        log.error("Could not set user authentication in security context", ex);
    }

    filterChain.doFilter(request, response);
}
```

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/security/JwtAuthenticationFilter.java
git commit -m "feat: check token denylist in JwtAuthenticationFilter"
```

---

## Task 8: Adicionar Access Token ao Denylist no Logout

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/AuthService.java`
- Modify: `src/main/java/br/com/stockshift/controller/AuthController.java`

**Step 1: Modificar AuthService.logout para aceitar access token**

Adicionar dependência e modificar método:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;
    private final TokenDenylistService tokenDenylistService;
```

Modificar o método `logout`:

```java
@Transactional
public void logout(String accessToken, String refreshTokenValue) {
    // Revoke refresh token in database
    if (refreshTokenValue != null) {
        refreshTokenService.revokeRefreshToken(refreshTokenValue);
    }

    // Add access token to denylist
    if (accessToken != null) {
        try {
            String jti = jwtTokenProvider.getJtiFromToken(accessToken);
            long ttl = jwtTokenProvider.getRemainingTtl(accessToken);
            if (ttl > 0) {
                tokenDenylistService.addToDenylist(jti, ttl);
                log.debug("Access token added to denylist: {}", jti);
            }
        } catch (Exception e) {
            log.warn("Failed to add access token to denylist: {}", e.getMessage());
        }
    }
}
```

**Step 2: Modificar AuthController.logout para passar access token**

Adicionar método helper no CookieUtil (ou inline):

```java
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
```

**Step 3: Adicionar método getAccessTokenFromCookie ao CookieUtil**

Verificar se já existe, senão adicionar:

```java
public String getAccessTokenFromCookie(Cookie[] cookies) {
    if (cookies == null) {
        return null;
    }
    for (Cookie cookie : cookies) {
        if ("accessToken".equals(cookie.getName())) {
            return cookie.getValue();
        }
    }
    return null;
}
```

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/service/AuthService.java src/main/java/br/com/stockshift/controller/AuthController.java src/main/java/br/com/stockshift/util/CookieUtil.java
git commit -m "feat: add access token to denylist on logout"
```

---

## Task 9: Teste de Integração

**Files:**
- Create: `src/test/java/br/com/stockshift/integration/TokenDenylistIntegrationTest.java`

**Step 1: Escrever teste de integração**

```java
package br.com.stockshift.integration;

import br.com.stockshift.dto.auth.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenDenylistIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void afterLogout_accessTokenShouldBeRejected() throws Exception {
        // 1. Login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@test.com");
        loginRequest.setPassword("test123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie accessTokenCookie = loginResult.getResponse().getCookie("accessToken");
        Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");

        // 2. Verify access works before logout
        mockMvc.perform(get("/api/warehouses")
                        .cookie(accessTokenCookie))
                .andExpect(status().isOk());

        // 3. Logout
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(accessTokenCookie)
                        .cookie(refreshTokenCookie))
                .andExpect(status().isOk());

        // 4. Verify access is REJECTED after logout
        mockMvc.perform(get("/api/warehouses")
                        .cookie(accessTokenCookie))
                .andExpect(status().isUnauthorized());
    }
}
```

**Step 2: Executar teste (deve falhar inicialmente se Redis não estiver disponível)**

Run: `./gradlew test --tests TokenDenylistIntegrationTest`
Expected: Depende da configuração do ambiente de teste

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/integration/TokenDenylistIntegrationTest.java
git commit -m "test: add integration test for token denylist on logout"
```

---

## Task 10: Verificação Final e Build

**Step 1: Executar todos os testes**

Run: `./gradlew test`
Expected: PASS

**Step 2: Verificar build completo**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 3: Commit final (se houver ajustes)**

```bash
git add -A
git commit -m "chore: final adjustments for JWT denylist feature"
```

---

## Checklist de Verificação Manual

Após deploy com Redis disponível:

- [ ] Login gera token com `jti` (verificar JWT no jwt.io)
- [ ] Logout adiciona entrada no Redis (`redis-cli KEYS "token:denylist:*"`)
- [ ] Request com token após logout retorna 401
- [ ] TTL do Redis corresponde ao tempo restante do token
- [ ] Tokens expirados são removidos automaticamente do Redis

---

## Dependências de Infraestrutura

Para rodar localmente/produção, é necessário:

```bash
# Docker Redis para desenvolvimento local
docker run -d --name redis -p 6379:6379 redis:7-alpine

# Variáveis de ambiente para produção
REDIS_HOST=your-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=your-password
```
