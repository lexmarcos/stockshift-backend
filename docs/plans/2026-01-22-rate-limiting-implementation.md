# Rate Limiting Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implementar rate limiting no endpoint `/api/auth/login` para prevenir ataques de força bruta (vuln-0002).

**Architecture:** Filtro Spring intercepta requests de login, consulta Bucket4j+Redis para verificar limite (5 tentativas/15min por IP), retorna HTTP 429 se excedido.

**Tech Stack:** Spring Boot 4.0.1, Bucket4j 8.10.1, Redis (Lettuce), Java 17

---

## Task 1: Adicionar Dependências Bucket4j

**Files:**
- Modify: `build.gradle:27-72`

**Step 1: Adicionar dependências no build.gradle**

Adicionar após a linha 58 (após OWASP encoder):

```gradle
	// Rate Limiting
	implementation 'com.bucket4j:bucket4j-core:8.10.1'
	implementation 'com.bucket4j:bucket4j-redis:8.10.1'
```

**Step 2: Verificar que compila**

Run: `./gradlew compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add build.gradle
git commit -m "deps: add bucket4j for rate limiting"
```

---

## Task 2: Criar RateLimitProperties

**Files:**
- Create: `src/main/java/br/com/stockshift/config/RateLimitProperties.java`
- Modify: `src/main/resources/application.yml`

**Step 1: Criar classe de configuração**

```java
package br.com.stockshift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rate-limit.login")
@Data
public class RateLimitProperties {

    /**
     * Maximum number of login attempts allowed in the time window.
     */
    private int capacity = 5;

    /**
     * Number of tokens to refill after the duration expires.
     */
    private int refillTokens = 5;

    /**
     * Duration in minutes for the refill period.
     */
    private int refillDurationMinutes = 15;
}
```

**Step 2: Adicionar configuração no application.yml**

Adicionar no final do arquivo:

```yaml

# Rate Limiting
rate-limit:
  login:
    capacity: 5
    refill-tokens: 5
    refill-duration-minutes: 15
```

**Step 3: Verificar que compila**

Run: `./gradlew compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/config/RateLimitProperties.java
git add src/main/resources/application.yml
git commit -m "feat: add rate limit configuration properties"
```

---

## Task 3: Criar RateLimitExceededException

**Files:**
- Create: `src/main/java/br/com/stockshift/exception/RateLimitExceededException.java`

**Step 1: Criar exception**

```java
package br.com.stockshift.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Too many login attempts. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
```

**Step 2: Verificar que compila**

Run: `./gradlew compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/exception/RateLimitExceededException.java
git commit -m "feat: add RateLimitExceededException"
```

---

## Task 4: Adicionar Handler no GlobalExceptionHandler

**Files:**
- Modify: `src/main/java/br/com/stockshift/exception/GlobalExceptionHandler.java`

**Step 1: Adicionar import**

Adicionar após linha 8:

```java
import br.com.stockshift.exception.RateLimitExceededException;
```

**Step 2: Adicionar handler method**

Adicionar após o método `handleBadCredentialsException` (após linha 129):

```java

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceededException(
            RateLimitExceededException ex,
            WebRequest request
    ) {
        log.warn("Rate limit exceeded for request: {}",
                request.getDescription(false).replace("uri=", ""));

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(error);
    }
```

**Step 3: Verificar que compila**

Run: `./gradlew compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/exception/GlobalExceptionHandler.java
git commit -m "feat: add rate limit exception handler with Retry-After header"
```

---

## Task 5: Criar RateLimitService

**Files:**
- Create: `src/main/java/br/com/stockshift/security/ratelimit/RateLimitService.java`

**Step 1: Criar diretório**

Run: `mkdir -p src/main/java/br/com/stockshift/security/ratelimit`

**Step 2: Criar service**

```java
package br.com.stockshift.security.ratelimit;

import br.com.stockshift.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RateLimitProperties properties;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    private static final String KEY_PREFIX = "rate_limit:login:";

    private RedisClient redisClient;
    private StatefulRedisConnection<String, byte[]> connection;
    private ProxyManager<String> proxyManager;

    @PostConstruct
    public void init() {
        String redisUri = buildRedisUri();
        this.redisClient = RedisClient.create(redisUri);
        this.connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                        Duration.ofMinutes(properties.getRefillDurationMinutes() * 2)))
                .build();

        log.info("RateLimitService initialized with capacity={}, refillTokens={}, refillDuration={}min",
                properties.getCapacity(),
                properties.getRefillTokens(),
                properties.getRefillDurationMinutes());
    }

    @PreDestroy
    public void destroy() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    private String buildRedisUri() {
        if (redisPassword != null && !redisPassword.isEmpty()) {
            return String.format("redis://%s@%s:%d", redisPassword, redisHost, redisPort);
        }
        return String.format("redis://%s:%d", redisHost, redisPort);
    }

    /**
     * Attempts to consume one token from the bucket for the given client IP.
     *
     * @param clientIp the client's IP address
     * @return true if the request is allowed, false if rate limit exceeded
     */
    public boolean tryConsume(String clientIp) {
        String key = KEY_PREFIX + clientIp;

        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.getCapacity())
                        .refillGreedy(properties.getRefillTokens(),
                                Duration.ofMinutes(properties.getRefillDurationMinutes()))
                        .build())
                .build();

        return proxyManager.builder()
                .build(key, () -> configuration)
                .tryConsume(1);
    }

    /**
     * Gets the number of seconds until the rate limit resets.
     *
     * @return seconds until refill
     */
    public long getRetryAfterSeconds() {
        return properties.getRefillDurationMinutes() * 60L;
    }
}
```

**Step 3: Verificar que compila**

Run: `./gradlew compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/security/ratelimit/RateLimitService.java
git commit -m "feat: add RateLimitService with Bucket4j and Redis"
```

---

## Task 6: Criar RateLimitFilter

**Files:**
- Create: `src/main/java/br/com/stockshift/security/ratelimit/RateLimitFilter.java`

**Step 1: Criar filter**

```java
package br.com.stockshift.security.ratelimit;

import br.com.stockshift.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    private static final String LOGIN_PATH = "/stockshift/api/auth/login";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!isLoginRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        log.debug("Rate limit check for IP: {} on path: {}", clientIp, request.getRequestURI());

        if (!rateLimitService.tryConsume(clientIp)) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);

            long retryAfter = rateLimitService.getRetryAfterSeconds();

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfter));

            ApiResponse<?> error = ApiResponse.error(
                    "Muitas tentativas de login. Tente novamente em " +
                    (retryAfter / 60) + " minutos."
            );

            objectMapper.writeValue(response.getOutputStream(), error);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && LOGIN_PATH.equals(request.getRequestURI());
    }

    /**
     * Extracts the client IP address, considering proxy headers.
     * Priority: X-Forwarded-For > X-Real-IP > CF-Connecting-IP > RemoteAddr
     */
    private String extractClientIp(HttpServletRequest request) {
        // Cloudflare
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp.trim();
        }

        // Standard proxy header
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, first one is the original client
            return xForwardedFor.split(",")[0].trim();
        }

        // Nginx proxy
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr();
    }
}
```

**Step 2: Verificar que compila**

Run: `./gradlew compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/security/ratelimit/RateLimitFilter.java
git commit -m "feat: add RateLimitFilter for login endpoint"
```

---

## Task 7: Criar Teste Unitário do RateLimitService

**Files:**
- Create: `src/test/java/br/com/stockshift/security/ratelimit/RateLimitServiceTest.java`

**Step 1: Criar diretório de teste**

Run: `mkdir -p src/test/java/br/com/stockshift/security/ratelimit`

**Step 2: Criar teste**

```java
package br.com.stockshift.security.ratelimit;

import br.com.stockshift.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RateLimitServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("rate-limit.login.capacity", () -> 3);
        registry.add("rate-limit.login.refill-tokens", () -> 3);
        registry.add("rate-limit.login.refill-duration-minutes", () -> 1);
    }

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private RateLimitProperties properties;

    @Test
    @DisplayName("Should allow requests within limit")
    void shouldAllowRequestsWithinLimit() {
        String ip = "192.168.1.100";

        for (int i = 0; i < properties.getCapacity(); i++) {
            assertThat(rateLimitService.tryConsume(ip))
                    .as("Attempt %d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should block requests over limit")
    void shouldBlockRequestsOverLimit() {
        String ip = "192.168.1.101";

        // Consume all tokens
        for (int i = 0; i < properties.getCapacity(); i++) {
            rateLimitService.tryConsume(ip);
        }

        // Next attempt should be blocked
        assertThat(rateLimitService.tryConsume(ip))
                .as("Request over limit should be blocked")
                .isFalse();
    }

    @Test
    @DisplayName("Should track different IPs separately")
    void shouldTrackDifferentIpsSeparately() {
        String ip1 = "192.168.1.102";
        String ip2 = "192.168.1.103";

        // Consume all tokens for IP1
        for (int i = 0; i < properties.getCapacity(); i++) {
            rateLimitService.tryConsume(ip1);
        }

        // IP1 should be blocked
        assertThat(rateLimitService.tryConsume(ip1)).isFalse();

        // IP2 should still be allowed
        assertThat(rateLimitService.tryConsume(ip2)).isTrue();
    }

    @Test
    @DisplayName("Should return correct retry-after seconds")
    void shouldReturnCorrectRetryAfterSeconds() {
        long retryAfter = rateLimitService.getRetryAfterSeconds();

        assertThat(retryAfter)
                .isEqualTo(properties.getRefillDurationMinutes() * 60L);
    }
}
```

**Step 3: Rodar teste**

Run: `./gradlew test --tests "br.com.stockshift.security.ratelimit.RateLimitServiceTest" --no-daemon`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 4: Commit**

```bash
git add src/test/java/br/com/stockshift/security/ratelimit/RateLimitServiceTest.java
git commit -m "test: add RateLimitService unit tests"
```

---

## Task 8: Criar Teste de Integração

**Files:**
- Create: `src/test/java/br/com/stockshift/security/ratelimit/RateLimitIntegrationTest.java`

**Step 1: Criar teste de integração**

```java
package br.com.stockshift.security.ratelimit;

import br.com.stockshift.dto.auth.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RateLimitIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("stockshift_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // Rate limit with low threshold for testing
        registry.add("rate-limit.login.capacity", () -> 3);
        registry.add("rate-limit.login.refill-tokens", () -> 3);
        registry.add("rate-limit.login.refill-duration-minutes", () -> 15);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should return 429 when rate limit exceeded")
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        LoginRequest request = new LoginRequest("test@test.com", "wrongpassword");
        String jsonRequest = objectMapper.writeValueAsString(request);

        // Make requests up to the limit (3 attempts)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/stockshift/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonRequest)
                            .header("X-Forwarded-For", "10.0.0.200"))
                    .andExpect(status().isUnauthorized()); // Wrong credentials
        }

        // 4th attempt should be rate limited
        mockMvc.perform(post("/stockshift/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest)
                        .header("X-Forwarded-For", "10.0.0.200"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Muitas tentativas")));
    }

    @Test
    @DisplayName("Should allow requests from different IPs")
    void shouldAllowRequestsFromDifferentIps() throws Exception {
        LoginRequest request = new LoginRequest("test@test.com", "wrongpassword");
        String jsonRequest = objectMapper.writeValueAsString(request);

        // Exhaust limit for IP1
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/stockshift/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonRequest)
                            .header("X-Forwarded-For", "10.0.0.201"));
        }

        // IP1 should be blocked
        mockMvc.perform(post("/stockshift/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest)
                        .header("X-Forwarded-For", "10.0.0.201"))
                .andExpect(status().isTooManyRequests());

        // IP2 should still work
        mockMvc.perform(post("/stockshift/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest)
                        .header("X-Forwarded-For", "10.0.0.202"))
                .andExpect(status().isUnauthorized()); // Wrong credentials, but not rate limited
    }
}
```

**Step 2: Rodar teste**

Run: `./gradlew test --tests "br.com.stockshift.security.ratelimit.RateLimitIntegrationTest" --no-daemon`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/security/ratelimit/RateLimitIntegrationTest.java
git commit -m "test: add rate limiting integration tests"
```

---

## Task 9: Teste Manual e Validação Final

**Step 1: Rodar todos os testes**

Run: `./gradlew test --no-daemon`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 2: Iniciar aplicação localmente (se possível)**

Run: `./gradlew bootRun --no-daemon`

**Step 3: Testar com curl (em outro terminal)**

```bash
# Fazer 6 tentativas de login
for i in {1..6}; do
  echo "Attempt $i:"
  curl -s -w "\nHTTP Status: %{http_code}\n" \
    -X POST http://localhost:9000/stockshift/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"test@test.com","password":"wrong"}' \
    | head -5
  echo "---"
done
```

Expected:
- Attempts 1-5: HTTP Status 401 (Unauthorized - wrong credentials)
- Attempt 6: HTTP Status 429 (Too Many Requests)

**Step 4: Commit final**

```bash
git add -A
git commit -m "feat: implement rate limiting for login endpoint (vuln-0002)

- Add Bucket4j + Redis for distributed rate limiting
- Limit: 5 attempts per 15 minutes per IP
- Returns HTTP 429 with Retry-After header when exceeded
- Supports proxy headers (X-Forwarded-For, CF-Connecting-IP)
- Includes unit and integration tests

Fixes: vuln-0002 (CVSS 8.2)"
```

---

## Checklist Final

- [ ] Dependências Bucket4j adicionadas
- [ ] RateLimitProperties configurado
- [ ] RateLimitExceededException criada
- [ ] GlobalExceptionHandler atualizado
- [ ] RateLimitService implementado
- [ ] RateLimitFilter implementado
- [ ] Testes unitários passando
- [ ] Testes de integração passando
- [ ] Teste manual com curl confirmado
- [ ] Commits realizados
