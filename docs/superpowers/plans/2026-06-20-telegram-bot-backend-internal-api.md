# Telegram Bot Backend Internal API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build bot-only StockShift backend endpoints that authenticate with `X-StockShift-Bot-Key` and return tenant-scoped warehouse and product stock data for the Telegram bot.

**Architecture:** Add a dedicated internal auth filter for `/api/internal/bot/**`, set `TenantContext` from backend configuration, and expose focused internal controllers. Keep product aggregation in the backend so the Telegram bot does not duplicate StockShift domain rules or read the database directly.

**Tech Stack:** Java 17, Spring Boot 4.0.1, Spring Security, Spring Data JPA, PostgreSQL, JUnit 5, MockMvc, Testcontainers.

## Global Constraints

- App context path stays `/stockshift`; controller mappings use `/api/...` internally.
- Bot auth uses header `X-StockShift-Bot-Key`.
- Bot auth is valid only for `/api/internal/bot/**`.
- Normal endpoints continue requiring JWT and permissions.
- Bot requests use one configured tenant: `STOCKSHIFT_BOT_TENANT_ID`.
- Batch prices are cents in `Long`.
- Batch quantities are `BigDecimal`.
- Latest batch is selected by `createdAt DESC, id DESC` among non-deleted batches.
- Product search result `limit` defaults to `5` and is capped at `10`.
- Do not persist Telegram questions, audio, transcriptions, or answers in the backend.
- Follow `stockshift-backend/AGENTS.md`: functions 4-20 lines, focused files, constructor injection, tests for every new function.

---

## File Structure

- Create `src/main/java/br/com/stockshift/config/BotAuthenticationProperties.java`: typed configuration for the internal bot key and tenant ID.
- Create `src/main/java/br/com/stockshift/security/BotPrincipal.java`: principal object for authenticated bot requests.
- Create `src/main/java/br/com/stockshift/security/BotAuthenticationFilter.java`: validates `X-StockShift-Bot-Key`, sets `TenantContext`, and creates a bot authority.
- Modify `src/main/java/br/com/stockshift/config/SecurityConfig.java`: wires the bot filter and restricts internal bot routes.
- Modify `src/main/resources/application.yml`: adds `stockshift.bot` configuration.
- Modify `src/main/resources/application-dev.example.yml`, `src/main/resources/application-prod.yml`, `src/test/resources/application-test.yml`, `.env.example`: document/configure bot env variables.
- Create `src/main/java/br/com/stockshift/dto/internal/bot/BotWarehouseResponse.java`: warehouse DTO for bot commands and warehouse resolution.
- Modify `src/main/java/br/com/stockshift/repository/WarehouseRepository.java`: active tenant-scoped list/search queries for bot use.
- Create `src/main/java/br/com/stockshift/service/internal/BotWarehouseService.java`: maps active warehouses from `TenantContext`.
- Create `src/main/java/br/com/stockshift/controller/internal/BotWarehouseController.java`: exposes `/api/internal/bot/warehouses` routes.
- Create `src/main/java/br/com/stockshift/dto/internal/bot/BotProductSearchProjection.java`: native projection for aggregate product query.
- Create `src/main/java/br/com/stockshift/dto/internal/bot/BotProductSearchResultResponse.java`: one product match.
- Create `src/main/java/br/com/stockshift/dto/internal/bot/BotProductSearchResponse.java`: result list plus `hasMore`.
- Create `src/main/java/br/com/stockshift/repository/BotProductSearchRepository.java`: native aggregate query for product matches.
- Create `src/main/java/br/com/stockshift/service/internal/BotProductSearchService.java`: validates inputs, caps limits, maps projections.
- Create `src/main/java/br/com/stockshift/controller/internal/BotProductController.java`: exposes `/api/internal/bot/products/search`.
- Create `src/test/java/br/com/stockshift/security/BotAuthenticationFilterTest.java`: unit coverage for filter behavior.
- Create `src/test/java/br/com/stockshift/controller/internal/BotWarehouseControllerIntegrationTest.java`: integration coverage for warehouse routes.
- Create `src/test/java/br/com/stockshift/controller/internal/BotProductControllerIntegrationTest.java`: integration coverage for aggregate product search.
- Create `docs/endpoints/internal-bot.md`: internal API documentation.

---

### Task 1: Bot API Key Authentication

**Files:**
- Create: `stockshift-backend/src/main/java/br/com/stockshift/config/BotAuthenticationProperties.java`
- Create: `stockshift-backend/src/main/java/br/com/stockshift/security/BotPrincipal.java`
- Create: `stockshift-backend/src/main/java/br/com/stockshift/security/BotAuthenticationFilter.java`
- Modify: `stockshift-backend/src/main/java/br/com/stockshift/config/SecurityConfig.java`
- Modify: `stockshift-backend/src/main/resources/application.yml`
- Modify: `stockshift-backend/src/test/resources/application-test.yml`
- Test: `stockshift-backend/src/test/java/br/com/stockshift/security/BotAuthenticationFilterTest.java`

**Interfaces:**
- Consumes: `TenantContext.setTenantId(UUID)`, `TenantContext.clear()`, `WarehouseContext.clear()`.
- Produces: `BotAuthenticationFilter.BOT_AUTHORITY`, `BotAuthenticationFilter.HEADER_NAME`, authenticated `BotPrincipal(UUID tenantId)`, and `stockshift.bot.api-key` / `stockshift.bot.tenant-id` configuration.

- [ ] **Step 1: Write the failing filter test**

Create `src/test/java/br/com/stockshift/security/BotAuthenticationFilterTest.java`:

```java
package br.com.stockshift.security;

import br.com.stockshift.config.BotAuthenticationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BotAuthenticationFilterTest {

    private static final String API_KEY = "test-bot-key";
    private final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private BotAuthenticationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        BotAuthenticationProperties properties = new BotAuthenticationProperties();
        properties.setApiKey(API_KEY);
        properties.setTenantId(tenantId);
        filter = new BotAuthenticationFilter(properties);
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        WarehouseContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateInternalBotRouteWithValidKey() throws ServletException, IOException {
        MockHttpServletRequest request = internalRequest("/api/internal/bot/warehouses");
        request.addHeader(BotAuthenticationFilter.HEADER_NAME, API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting(authority -> authority.getAuthority())
                    .contains(BotAuthenticationFilter.BOT_AUTHORITY);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectInternalBotRouteWithoutKey() throws ServletException, IOException {
        MockHttpServletRequest request = internalRequest("/api/internal/bot/warehouses");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectInternalBotRouteWithInvalidKey() throws ServletException, IOException {
        MockHttpServletRequest request = internalRequest("/api/internal/bot/warehouses");
        request.addHeader(BotAuthenticationFilter.HEADER_NAME, "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateNormalApiRouteWithBotKey() throws ServletException, IOException {
        MockHttpServletRequest request = internalRequest("/api/products");
        request.addHeader(BotAuthenticationFilter.HEADER_NAME, API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest internalRequest(String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", servletPath);
        request.setServletPath(servletPath);
        return request;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.security.BotAuthenticationFilterTest"`

Expected: FAIL because `BotAuthenticationProperties`, `BotPrincipal`, and `BotAuthenticationFilter` do not exist.

- [ ] **Step 3: Add bot auth properties and principal**

Create `src/main/java/br/com/stockshift/config/BotAuthenticationProperties.java`:

```java
package br.com.stockshift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Configuration
@ConfigurationProperties(prefix = "stockshift.bot")
@Data
public class BotAuthenticationProperties {
    private String apiKey;
    private UUID tenantId;

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) && tenantId != null;
    }
}
```

Create `src/main/java/br/com/stockshift/security/BotPrincipal.java`:

```java
package br.com.stockshift.security;

import java.util.UUID;

public record BotPrincipal(UUID tenantId) {
}
```

- [ ] **Step 4: Add bot authentication filter**

Create `src/main/java/br/com/stockshift/security/BotAuthenticationFilter.java`:

```java
package br.com.stockshift.security;

import br.com.stockshift.config.BotAuthenticationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BotAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-StockShift-Bot-Key";
    public static final String BOT_AUTHORITY = "bot:internal";
    private static final AntPathRequestMatcher BOT_ROUTE_MATCHER =
            new AntPathRequestMatcher("/api/internal/bot/**");

    private final BotAuthenticationProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !BOT_ROUTE_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isValidRequest(request)) {
            reject(response);
            return;
        }

        try {
            authenticateBotRequest(request);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            WarehouseContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isValidRequest(HttpServletRequest request) {
        String submittedKey = request.getHeader(HEADER_NAME);
        return properties.isConfigured()
                && StringUtils.hasText(submittedKey)
                && submittedKey.equals(properties.getApiKey());
    }

    private void authenticateBotRequest(HttpServletRequest request) {
        TenantContext.setTenantId(properties.getTenantId());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                new BotPrincipal(properties.getTenantId()),
                null,
                List.of(new SimpleGrantedAuthority(BOT_AUTHORITY)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"Invalid bot API key\"}");
    }
}
```

- [ ] **Step 5: Wire the filter into Spring Security**

Modify `src/main/java/br/com/stockshift/config/SecurityConfig.java`:

```java
package br.com.stockshift.config;

import br.com.stockshift.security.BotAuthenticationFilter;
import br.com.stockshift.security.JwtAuthenticationFilter;
import br.com.stockshift.security.audit.AuditContextFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final BotAuthenticationFilter botAuthenticationFilter;
  private final AuditContextFilter auditContextFilter;
  private final CorsConfigurationSource corsConfigurationSource;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**",
                "/swagger-resources/**",
                "/webjars/**")
            .permitAll()
            .requestMatchers("/actuator/health/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/register").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/sales/infinitepay/webhook/*").permitAll()
            .requestMatchers("/api/internal/bot/**").hasAuthority(BotAuthenticationFilter.BOT_AUTHORITY)
            .anyRequest().authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(botAuthenticationFilter, JwtAuthenticationFilter.class)
        .addFilterAfter(auditContextFilter, BotAuthenticationFilter.class);

    return http.build();
  }
}
```

- [ ] **Step 6: Add backend configuration keys**

Append to `src/main/resources/application.yml`:

```yaml
stockshift:
  bot:
    api-key: ${STOCKSHIFT_BOT_API_KEY:}
    tenant-id: ${STOCKSHIFT_BOT_TENANT_ID:}
```

Append to `src/test/resources/application-test.yml`:

```yaml
stockshift:
  bot:
    api-key: test-bot-key
    tenant-id: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.stockshift.security.BotAuthenticationFilterTest"`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/br/com/stockshift/config/BotAuthenticationProperties.java src/main/java/br/com/stockshift/security/BotPrincipal.java src/main/java/br/com/stockshift/security/BotAuthenticationFilter.java src/main/java/br/com/stockshift/config/SecurityConfig.java src/main/resources/application.yml src/test/resources/application-test.yml src/test/java/br/com/stockshift/security/BotAuthenticationFilterTest.java
git commit -m "feat(bot): add internal API key authentication"
```

---

### Task 2: Internal Bot Warehouse API

**Files:**
- Create: `stockshift-backend/src/main/java/br/com/stockshift/dto/internal/bot/BotWarehouseResponse.java`
- Modify: `stockshift-backend/src/main/java/br/com/stockshift/repository/WarehouseRepository.java`
- Create: `stockshift-backend/src/main/java/br/com/stockshift/service/internal/BotWarehouseService.java`
- Create: `stockshift-backend/src/main/java/br/com/stockshift/controller/internal/BotWarehouseController.java`
- Test: `stockshift-backend/src/test/java/br/com/stockshift/controller/internal/BotWarehouseControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 bot authentication and `TenantContext.getTenantId()`.
- Produces: `GET /api/internal/bot/warehouses` and `GET /api/internal/bot/warehouses/search?query=<text>` returning `ApiResponse<List<BotWarehouseResponse>>`.

- [ ] **Step 1: Write failing warehouse integration tests**

Create `src/test/java/br/com/stockshift/controller/internal/BotWarehouseControllerIntegrationTest.java`:

```java
package br.com.stockshift.controller.internal;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BotWarehouseControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BOT_KEY = "test-bot-key";
    private static final UUID BOT_TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @BeforeEach
    void setUpData() {
        warehouseRepository.deleteAll();
        tenantRepository.deleteAll();
        createTenant(BOT_TENANT_ID, "Bot Tenant", "11111111000111");
        createTenant(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "Other Tenant", "22222222000122");
    }

    @Test
    void shouldListOnlyActiveBotTenantWarehouses() throws Exception {
        TestDataFactory.createWarehouse(warehouseRepository, BOT_TENANT_ID, "Centro");
        Warehouse inactive = TestDataFactory.createWarehouse(warehouseRepository, BOT_TENANT_ID, "Inativo");
        inactive.setIsActive(false);
        warehouseRepository.save(inactive);
        TestDataFactory.createWarehouse(warehouseRepository, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "Outro Tenant");

        mockMvc.perform(get("/api/internal/bot/warehouses")
                        .header("X-StockShift-Bot-Key", BOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Centro"));
    }

    @Test
    void shouldSearchActiveWarehousesByNameOrCode() throws Exception {
        Warehouse center = TestDataFactory.createWarehouse(warehouseRepository, BOT_TENANT_ID, "Deposito Centro");
        center.setCode("CTR");
        warehouseRepository.save(center);
        TestDataFactory.createWarehouse(warehouseRepository, BOT_TENANT_ID, "Zona Norte");

        mockMvc.perform(get("/api/internal/bot/warehouses/search")
                        .param("query", "ctr")
                        .header("X-StockShift-Bot-Key", BOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Deposito Centro"))
                .andExpect(jsonPath("$.data[0].code").value("CTR"));
    }

    @Test
    void shouldRejectWarehouseRouteWithoutBotKey() throws Exception {
        mockMvc.perform(get("/api/internal/bot/warehouses"))
                .andExpect(status().isUnauthorized());
    }

    private Tenant createTenant(UUID tenantId, String name, String document) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setBusinessName(name);
        tenant.setDocument(document);
        tenant.setEmail(document + "@test.com");
        tenant.setIsActive(true);
        return tenantRepository.saveAndFlush(tenant);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.controller.internal.BotWarehouseControllerIntegrationTest"`

Expected: FAIL with `404` for `/api/internal/bot/warehouses` or compilation failure because bot warehouse classes do not exist.

- [ ] **Step 3: Create warehouse DTO**

Create `src/main/java/br/com/stockshift/dto/internal/bot/BotWarehouseResponse.java`:

```java
package br.com.stockshift.dto.internal.bot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotWarehouseResponse {
    private UUID id;
    private String name;
    private String code;
    private String city;
    private String state;
}
```

- [ ] **Step 4: Add warehouse repository queries**

Modify `src/main/java/br/com/stockshift/repository/WarehouseRepository.java` by adding these methods below `findByTenantIdAndCode`:

```java
    @Query("SELECT w FROM Warehouse w WHERE w.tenantId = :tenantId AND w.isActive = true ORDER BY w.name ASC")
    List<Warehouse> findActiveByTenantId(UUID tenantId);

    @Query("""
            SELECT w FROM Warehouse w
            WHERE w.tenantId = :tenantId
              AND w.isActive = true
              AND (LOWER(w.name) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(w.code) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY w.name ASC
            """)
    List<Warehouse> searchActiveByTenantId(UUID tenantId, String query);
```

- [ ] **Step 5: Create warehouse service**

Create `src/main/java/br/com/stockshift/service/internal/BotWarehouseService.java`:

```java
package br.com.stockshift.service.internal;

import br.com.stockshift.dto.internal.bot.BotWarehouseResponse;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BotWarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public List<BotWarehouseResponse> findActiveWarehouses() {
        UUID tenantId = requireTenantId();
        return warehouseRepository.findActiveByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BotWarehouseResponse> searchActiveWarehouses(String query) {
        String sanitizedQuery = query == null ? "" : query.trim();
        if (sanitizedQuery.isBlank()) {
            return findActiveWarehouses();
        }
        UUID tenantId = requireTenantId();
        return warehouseRepository.searchActiveByTenantId(tenantId, sanitizedQuery).stream()
                .map(this::toResponse)
                .toList();
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Missing tenant context for bot warehouse request; expected STOCKSHIFT_BOT_TENANT_ID");
        }
        return tenantId;
    }

    private BotWarehouseResponse toResponse(Warehouse warehouse) {
        return BotWarehouseResponse.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .code(warehouse.getCode())
                .city(warehouse.getCity())
                .state(warehouse.getState())
                .build();
    }
}
```

- [ ] **Step 6: Create warehouse controller**

Create `src/main/java/br/com/stockshift/controller/internal/BotWarehouseController.java`:

```java
package br.com.stockshift.controller.internal;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.internal.bot.BotWarehouseResponse;
import br.com.stockshift.service.internal.BotWarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/internal/bot/warehouses")
@RequiredArgsConstructor
public class BotWarehouseController {

    private final BotWarehouseService botWarehouseService;

    @GetMapping
    @PreAuthorize("hasAuthority('bot:internal')")
    public ResponseEntity<ApiResponse<List<BotWarehouseResponse>>> findActiveWarehouses() {
        return ResponseEntity.ok(ApiResponse.success(botWarehouseService.findActiveWarehouses()));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('bot:internal')")
    public ResponseEntity<ApiResponse<List<BotWarehouseResponse>>> searchActiveWarehouses(
            @RequestParam String query) {
        return ResponseEntity.ok(ApiResponse.success(botWarehouseService.searchActiveWarehouses(query)));
    }
}
```

- [ ] **Step 7: Run warehouse integration tests**

Run: `./gradlew test --tests "br.com.stockshift.controller.internal.BotWarehouseControllerIntegrationTest"`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/internal/bot/BotWarehouseResponse.java src/main/java/br/com/stockshift/repository/WarehouseRepository.java src/main/java/br/com/stockshift/service/internal/BotWarehouseService.java src/main/java/br/com/stockshift/controller/internal/BotWarehouseController.java src/test/java/br/com/stockshift/controller/internal/BotWarehouseControllerIntegrationTest.java
git commit -m "feat(bot): add internal warehouse lookup API"
```

---

### Task 3: Internal Bot Product Search API

**Files:**
- Create: `stockshift-backend/src/main/java/br/com/stockshift/dto/internal/bot/BotProductSearchProjection.java`
- Create: `stockshift-backend/src/main/java/br/com/stockshift/dto/internal/bot/BotProductSearchResultResponse.java`
- Create: `stockshift-backend/src/main/java/br/com/stockshift/dto/internal/bot/BotProductSearchResponse.java`
- Create: `stockshift-backend/src/main/java/br/com/stockshift/repository/BotProductSearchRepository.java`
- Create: `stockshift-backend/src/main/java/br/com/stockshift/service/internal/BotProductSearchService.java`
- Create: `stockshift-backend/src/main/java/br/com/stockshift/controller/internal/BotProductController.java`
- Test: `stockshift-backend/src/test/java/br/com/stockshift/controller/internal/BotProductControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 bot authentication, Task 2 warehouse repository methods, `Batch` and `Product` tables.
- Produces: `GET /api/internal/bot/products/search?query=<text>&warehouseId=<uuid>&limit=<number>` returning `ApiResponse<BotProductSearchResponse>`.

- [ ] **Step 1: Write failing product integration tests**

Create `src/test/java/br/com/stockshift/controller/internal/BotProductControllerIntegrationTest.java`:

```java
package br.com.stockshift.controller.internal;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BotProductControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BOT_KEY = "test-bot-key";
    private static final UUID BOT_TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Warehouse warehouse;
    private Category category;

    @BeforeEach
    void setUpData() {
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        tenantRepository.deleteAll();
        createTenant(BOT_TENANT_ID, "Bot Tenant", "33333333000133");
        warehouse = TestDataFactory.createWarehouse(warehouseRepository, BOT_TENANT_ID, "Centro");
        category = TestDataFactory.createCategory(categoryRepository, BOT_TENANT_ID, "Perfumes");
    }

    @Test
    void shouldSearchProductByNameAndReturnStockAndLatestBatchPrice() throws Exception {
        Product product = TestDataFactory.createProduct(productRepository, BOT_TENANT_ID, category, "Perfume Gold", "SKU-GOLD");
        product.setImageUrl("https://cdn.example.com/products/gold.png");
        product.setBarcode("7891234567890");
        productRepository.save(product);
        Batch oldBatch = TestDataFactory.createBatch(batchRepository, BOT_TENANT_ID, product, warehouse, "OLD", 10);
        oldBatch.setSellingPrice(1000L);
        batchRepository.saveAndFlush(oldBatch);
        setCreatedAt(oldBatch.getId(), LocalDateTime.parse("2026-01-01T10:00:00"));
        Batch newBatch = TestDataFactory.createBatch(batchRepository, BOT_TENANT_ID, product, warehouse, "NEW", 15);
        newBatch.setSellingPrice(12990L);
        batchRepository.saveAndFlush(newBatch);
        setCreatedAt(newBatch.getId(), LocalDateTime.parse("2026-02-01T10:00:00"));

        mockMvc.perform(get("/api/internal/bot/products/search")
                        .param("query", "gold")
                        .param("warehouseId", warehouse.getId().toString())
                        .header("X-StockShift-Bot-Key", BOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.results.length()").value(1))
                .andExpect(jsonPath("$.data.results[0].name").value("Perfume Gold"))
                .andExpect(jsonPath("$.data.results[0].imageUrl").value("https://cdn.example.com/products/gold.png"))
                .andExpect(jsonPath("$.data.results[0].barcode").value("7891234567890"))
                .andExpect(jsonPath("$.data.results[0].totalQuantity").value(25))
                .andExpect(jsonPath("$.data.results[0].latestBatchSellingPrice").value(12990))
                .andExpect(jsonPath("$.data.results[0].latestBatchCode").value("NEW"));
    }

    @Test
    void shouldSearchProductBySkuAndBarcode() throws Exception {
        Product product = TestDataFactory.createProduct(productRepository, BOT_TENANT_ID, category, "Body Splash", "SKU-BODY");
        product.setBarcode("9998887776665");
        productRepository.save(product);
        TestDataFactory.createBatch(batchRepository, BOT_TENANT_ID, product, warehouse, "BODY", 3);

        mockMvc.perform(get("/api/internal/bot/products/search")
                        .param("query", "9998887776665")
                        .param("warehouseId", warehouse.getId().toString())
                        .header("X-StockShift-Bot-Key", BOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].sku").value("SKU-BODY"));
    }

    @Test
    void shouldReturnHasMoreWhenMoreResultsExistThanLimit() throws Exception {
        for (int index = 1; index <= 6; index++) {
            Product product = TestDataFactory.createProduct(productRepository, BOT_TENANT_ID, category,
                    "Perfume Match " + index, "SKU-MATCH-" + index);
            TestDataFactory.createBatch(batchRepository, BOT_TENANT_ID, product, warehouse, "BATCH-" + index, 1);
        }

        mockMvc.perform(get("/api/internal/bot/products/search")
                        .param("query", "Perfume Match")
                        .param("warehouseId", warehouse.getId().toString())
                        .param("limit", "5")
                        .header("X-StockShift-Bot-Key", BOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(5))
                .andExpect(jsonPath("$.data.hasMore").value(true));
    }

    @Test
    void shouldReturnEmptyResultsForNoMatch() throws Exception {
        mockMvc.perform(get("/api/internal/bot/products/search")
                        .param("query", "not-found")
                        .param("warehouseId", warehouse.getId().toString())
                        .header("X-StockShift-Bot-Key", BOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(0))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    private Tenant createTenant(UUID tenantId, String name, String document) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setBusinessName(name);
        tenant.setDocument(document);
        tenant.setEmail(document + "@test.com");
        tenant.setIsActive(true);
        return tenantRepository.saveAndFlush(tenant);
    }

    private void setCreatedAt(UUID batchId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE batches SET created_at = ? WHERE id = ?", Timestamp.valueOf(createdAt), batchId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.stockshift.controller.internal.BotProductControllerIntegrationTest"`

Expected: FAIL with `404` for `/api/internal/bot/products/search` or compilation failure because bot product classes do not exist.

- [ ] **Step 3: Create product response DTOs and projection**

Create `src/main/java/br/com/stockshift/dto/internal/bot/BotProductSearchProjection.java`:

```java
package br.com.stockshift.dto.internal.bot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface BotProductSearchProjection {
    UUID getProductId();
    String getName();
    String getImageUrl();
    String getBarcode();
    String getSku();
    UUID getWarehouseId();
    String getWarehouseName();
    BigDecimal getTotalQuantity();
    Long getLatestBatchSellingPrice();
    String getLatestBatchCode();
    LocalDateTime getLatestBatchCreatedAt();
}
```

Create `src/main/java/br/com/stockshift/dto/internal/bot/BotProductSearchResultResponse.java`:

```java
package br.com.stockshift.dto.internal.bot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotProductSearchResultResponse {
    private UUID productId;
    private String name;
    private String imageUrl;
    private String barcode;
    private String sku;
    private UUID warehouseId;
    private String warehouseName;
    private BigDecimal totalQuantity;
    private Long latestBatchSellingPrice;
    private String latestBatchCode;
    private LocalDateTime latestBatchCreatedAt;
}
```

Create `src/main/java/br/com/stockshift/dto/internal/bot/BotProductSearchResponse.java`:

```java
package br.com.stockshift.dto.internal.bot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotProductSearchResponse {
    private List<BotProductSearchResultResponse> results;
    private Boolean hasMore;
}
```

- [ ] **Step 4: Create aggregate repository query**

Create `src/main/java/br/com/stockshift/repository/BotProductSearchRepository.java`:

```java
package br.com.stockshift.repository;

import br.com.stockshift.dto.internal.bot.BotProductSearchProjection;
import br.com.stockshift.model.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BotProductSearchRepository extends JpaRepository<Batch, UUID> {

    @Query(value = """
            SELECT p.id AS "productId",
                   p.name AS "name",
                   p.image_url AS "imageUrl",
                   p.barcode AS "barcode",
                   p.sku AS "sku",
                   w.id AS "warehouseId",
                   w.name AS "warehouseName",
                   COALESCE(SUM(b.quantity), 0) AS "totalQuantity",
                   latest.selling_price AS "latestBatchSellingPrice",
                   latest.batch_code AS "latestBatchCode",
                   latest.created_at AS "latestBatchCreatedAt"
            FROM products p
            JOIN batches b ON b.product_id = p.id
                          AND b.warehouse_id = :warehouseId
                          AND b.tenant_id = :tenantId
                          AND b.deleted_at IS NULL
            JOIN warehouses w ON w.id = b.warehouse_id
                             AND w.tenant_id = :tenantId
                             AND w.is_active = true
            LEFT JOIN LATERAL (
                SELECT lb.selling_price, lb.batch_code, lb.created_at
                FROM batches lb
                WHERE lb.product_id = p.id
                  AND lb.warehouse_id = :warehouseId
                  AND lb.tenant_id = :tenantId
                  AND lb.deleted_at IS NULL
                ORDER BY lb.created_at DESC, lb.id DESC
                LIMIT 1
            ) latest ON true
            WHERE p.tenant_id = :tenantId
              AND p.deleted_at IS NULL
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:query AS text), '%'))
                   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', CAST(:query AS text), '%'))
                   OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', CAST(:query AS text), '%')))
            GROUP BY p.id, p.name, p.image_url, p.barcode, p.sku,
                     w.id, w.name,
                     latest.selling_price, latest.batch_code, latest.created_at
            ORDER BY p.name ASC, p.id ASC
            LIMIT :limitPlusOne
            """, nativeQuery = true)
    List<BotProductSearchProjection> searchProductsForBot(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("query") String query,
            @Param("limitPlusOne") int limitPlusOne);
}
```

- [ ] **Step 5: Create product search service**

Create `src/main/java/br/com/stockshift/service/internal/BotProductSearchService.java`:

```java
package br.com.stockshift.service.internal;

import br.com.stockshift.dto.internal.bot.BotProductSearchProjection;
import br.com.stockshift.dto.internal.bot.BotProductSearchResponse;
import br.com.stockshift.dto.internal.bot.BotProductSearchResultResponse;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.repository.BotProductSearchRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BotProductSearchService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;

    private final BotProductSearchRepository botProductSearchRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public BotProductSearchResponse search(String query, UUID warehouseId, Integer requestedLimit) {
        UUID tenantId = requireTenantId();
        String sanitizedQuery = requireQuery(query);
        validateWarehouse(tenantId, warehouseId);
        int limit = sanitizeLimit(requestedLimit);
        List<BotProductSearchProjection> matches = botProductSearchRepository.searchProductsForBot(
                tenantId, warehouseId, sanitizedQuery, limit + 1);
        return toResponse(matches, limit);
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Missing tenant context for bot product search; expected STOCKSHIFT_BOT_TENANT_ID");
        }
        return tenantId;
    }

    private String requireQuery(String query) {
        String sanitizedQuery = query == null ? "" : query.trim();
        if (sanitizedQuery.isBlank()) {
            throw new BadRequestException("Invalid bot product query ''; expected non-blank name, SKU, or barcode");
        }
        return sanitizedQuery;
    }

    private void validateWarehouse(UUID tenantId, UUID warehouseId) {
        if (warehouseId == null) {
            throw new BadRequestException("Invalid warehouseId null; expected UUID for bot product search");
        }
        warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)
                .filter(warehouse -> Boolean.TRUE.equals(warehouse.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", warehouseId));
    }

    private int sanitizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
    }

    private BotProductSearchResponse toResponse(List<BotProductSearchProjection> matches, int limit) {
        boolean hasMore = matches.size() > limit;
        List<BotProductSearchResultResponse> results = matches.stream()
                .limit(limit)
                .map(this::toResult)
                .toList();
        return BotProductSearchResponse.builder()
                .results(results)
                .hasMore(hasMore)
                .build();
    }

    private BotProductSearchResultResponse toResult(BotProductSearchProjection projection) {
        return BotProductSearchResultResponse.builder()
                .productId(projection.getProductId())
                .name(projection.getName())
                .imageUrl(projection.getImageUrl())
                .barcode(projection.getBarcode())
                .sku(projection.getSku())
                .warehouseId(projection.getWarehouseId())
                .warehouseName(projection.getWarehouseName())
                .totalQuantity(projection.getTotalQuantity())
                .latestBatchSellingPrice(projection.getLatestBatchSellingPrice())
                .latestBatchCode(projection.getLatestBatchCode())
                .latestBatchCreatedAt(projection.getLatestBatchCreatedAt())
                .build();
    }
}
```

- [ ] **Step 6: Create product controller**

Create `src/main/java/br/com/stockshift/controller/internal/BotProductController.java`:

```java
package br.com.stockshift.controller.internal;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.internal.bot.BotProductSearchResponse;
import br.com.stockshift.service.internal.BotProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/internal/bot/products")
@RequiredArgsConstructor
public class BotProductController {

    private final BotProductSearchService botProductSearchService;

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('bot:internal')")
    public ResponseEntity<ApiResponse<BotProductSearchResponse>> search(
            @RequestParam String query,
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) Integer limit) {
        BotProductSearchResponse response = botProductSearchService.search(query, warehouseId, limit);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

- [ ] **Step 7: Run product integration tests**

Run: `./gradlew test --tests "br.com.stockshift.controller.internal.BotProductControllerIntegrationTest"`

Expected: PASS.

- [ ] **Step 8: Run related backend tests**

Run: `./gradlew test --tests "br.com.stockshift.security.BotAuthenticationFilterTest" --tests "br.com.stockshift.controller.internal.BotWarehouseControllerIntegrationTest" --tests "br.com.stockshift.controller.internal.BotProductControllerIntegrationTest"`

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/internal/bot/BotProductSearchProjection.java src/main/java/br/com/stockshift/dto/internal/bot/BotProductSearchResultResponse.java src/main/java/br/com/stockshift/dto/internal/bot/BotProductSearchResponse.java src/main/java/br/com/stockshift/repository/BotProductSearchRepository.java src/main/java/br/com/stockshift/service/internal/BotProductSearchService.java src/main/java/br/com/stockshift/controller/internal/BotProductController.java src/test/java/br/com/stockshift/controller/internal/BotProductControllerIntegrationTest.java
git commit -m "feat(bot): add internal product search API"
```

---

### Task 4: Backend Configuration And Endpoint Documentation

**Files:**
- Modify: `stockshift-backend/.env.example`
- Modify: `stockshift-backend/src/main/resources/application-dev.example.yml`
- Modify: `stockshift-backend/src/main/resources/application-prod.yml`
- Create: `stockshift-backend/docs/endpoints/internal-bot.md`
- Modify: `stockshift-backend/README.md`

**Interfaces:**
- Consumes: Tasks 1-3 endpoints and env variables.
- Produces: documented deployment/config contract for the Telegram bot service.

- [ ] **Step 1: Add env examples**

Append to `.env.example`:

```dotenv
STOCKSHIFT_BOT_API_KEY=change-me
STOCKSHIFT_BOT_TENANT_ID=00000000-0000-0000-0000-000000000000
```

Append to `src/main/resources/application-dev.example.yml`:

```yaml
stockshift:
  bot:
    api-key: ${STOCKSHIFT_BOT_API_KEY:dev-bot-key-change-me}
    tenant-id: ${STOCKSHIFT_BOT_TENANT_ID:00000000-0000-0000-0000-000000000000}
```

Append to `src/main/resources/application-prod.yml`:

```yaml
stockshift:
  bot:
    api-key: ${STOCKSHIFT_BOT_API_KEY}
    tenant-id: ${STOCKSHIFT_BOT_TENANT_ID}
```

- [ ] **Step 2: Document internal bot endpoints**

Create `docs/endpoints/internal-bot.md`:

````markdown
# Internal Bot Endpoints

These endpoints are for the Telegram product query bot. They are authenticated with `X-StockShift-Bot-Key` and are not public user-facing API endpoints.

**Base URL:** `/api/internal/bot`

**Authentication:** `X-StockShift-Bot-Key: change-me`

## GET /api/internal/bot/warehouses

Returns active warehouses for the configured bot tenant.

```json
{
  "success": true,
  "data": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "name": "Centro",
      "code": "CTR",
      "city": "Sao Paulo",
      "state": "SP"
    }
  ]
}
```

## GET /api/internal/bot/warehouses/search?query=centro

Searches active warehouses by name or code for the configured bot tenant.

## GET /api/internal/bot/products/search

Query parameters:

- `query`: product name, SKU, or barcode.
- `warehouseId`: UUID of the selected warehouse.
- `limit`: maximum result count. Defaults to `5` and is capped at `10`.

The endpoint returns product matches that have non-deleted batches in the selected warehouse. `totalQuantity` is summed across non-deleted batches. Latest batch price uses `createdAt DESC, id DESC`.

```json
{
  "success": true,
  "data": {
    "results": [
      {
        "productId": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Perfume Gold",
        "imageUrl": "https://cdn.example.com/products/gold.png",
        "barcode": "7891234567890",
        "sku": "SKU-GOLD",
        "warehouseId": "660e8400-e29b-41d4-a716-446655440001",
        "warehouseName": "Centro",
        "totalQuantity": 25,
        "latestBatchSellingPrice": 12990,
        "latestBatchCode": "NEW",
        "latestBatchCreatedAt": "2026-02-01T10:00:00"
      }
    ],
    "hasMore": false
  }
}
```
````

- [ ] **Step 3: Update README configuration table**

Add these rows under the existing configuration table in `README.md`:

```markdown
| `STOCKSHIFT_BOT_API_KEY` | Shared secret accepted by internal Telegram bot endpoints | empty |
| `STOCKSHIFT_BOT_TENANT_ID` | Tenant UUID used for internal Telegram bot queries | empty |
```

- [ ] **Step 4: Run backend tests**

Run: `./gradlew test --tests "br.com.stockshift.security.BotAuthenticationFilterTest" --tests "br.com.stockshift.controller.internal.BotWarehouseControllerIntegrationTest" --tests "br.com.stockshift.controller.internal.BotProductControllerIntegrationTest"`

Expected: PASS.

- [ ] **Step 5: Run full backend verification**

Run: `./gradlew check`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .env.example src/main/resources/application-dev.example.yml src/main/resources/application-prod.yml docs/endpoints/internal-bot.md README.md
git commit -m "docs(bot): document internal bot backend API"
```

---

## Self-Review Checklist

- Spec coverage: bot API key, tenant context, warehouse list/search, product aggregate search, latest batch rule, total quantity, limit cap, env docs, and tests are all covered.
- Red-flag scan: this plan contains concrete paths, commands, env names, route names, and code snippets.
- Type consistency: `BotProductSearchResponse`, `BotProductSearchResultResponse`, and `BotProductSearchProjection` names match across repository, service, controller, and tests.
- Execution dependency: Task 2 depends on Task 1; Task 3 depends on Tasks 1 and 2; Task 4 depends on Tasks 1-3.
