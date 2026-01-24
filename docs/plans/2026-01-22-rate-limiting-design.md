# Rate Limiting para Autenticação - Design

**Data:** 2026-01-22
**Vulnerabilidade:** vuln-0002 (CVSS 8.2 - HIGH)
**Status:** Aprovado

## Contexto

O endpoint `/api/auth/login` não possui rate limiting, permitindo ataques de força bruta ilimitados. O pentest identificou que 20+ tentativas consecutivas são aceitas sem bloqueio.

## Decisões

| Aspecto | Decisão |
|---------|---------|
| **Storage** | Redis (já configurado no projeto) |
| **Endpoints** | Apenas `/api/auth/login` |
| **Estratégia** | 5 tentativas / 15 min por IP |
| **Biblioteca** | Bucket4j + Redis |

## Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│                      Request Flow                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│   Client Request                                             │
│        │                                                     │
│        ▼                                                     │
│   ┌─────────────────────┐                                   │
│   │  RateLimitFilter    │  ◄── Intercepta antes do login    │
│   │  (Spring Filter)    │                                   │
│   └──────────┬──────────┘                                   │
│              │                                               │
│              ▼                                               │
│   ┌─────────────────────┐      ┌─────────────────────┐      │
│   │  RateLimitService   │ ───► │      Redis          │      │
│   │  (Bucket4j)         │      │  (Token Buckets)    │      │
│   └──────────┬──────────┘      └─────────────────────┘      │
│              │                                               │
│         ┌────┴────┐                                          │
│         │         │                                          │
│    Permitido   Bloqueado                                     │
│         │         │                                          │
│         ▼         ▼                                          │
│   AuthController  HTTP 429                                   │
│                   Too Many Requests                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Componentes:**
1. **RateLimitFilter** - Filtro Spring que intercepta requests para `/api/auth/login`
2. **RateLimitService** - Gerencia buckets no Redis usando Bucket4j
3. **Redis** - Armazena os token buckets por IP

## Implementação

### 1. Dependências (build.gradle)

```gradle
// Rate Limiting com Bucket4j + Redis
implementation 'com.bucket4j:bucket4j-core:8.10.1'
implementation 'com.bucket4j:bucket4j-redis:8.10.1'
```

### 2. Configuração (application.yml)

```yaml
rate-limit:
  login:
    capacity: 5           # máximo de tokens no bucket
    refill-tokens: 5      # quantos tokens repor
    refill-duration: 15   # a cada X minutos
```

### 3. Estrutura de Arquivos

```
src/main/java/br/com/stockshift/
├── config/
│   └── RateLimitProperties.java    # Configurações
├── security/
│   ├── ratelimit/
│   │   ├── RateLimitService.java   # Lógica Bucket4j + Redis
│   │   └── RateLimitFilter.java    # Filtro HTTP
└── exception/
    └── RateLimitExceededException.java  # Exception customizada
```

### 4. RateLimitProperties.java

```java
@Configuration
@ConfigurationProperties(prefix = "rate-limit.login")
@Data
public class RateLimitProperties {
    private int capacity = 5;          // máx tentativas
    private int refillTokens = 5;      // tokens repostos
    private int refillDuration = 15;   // minutos
}
```

### 5. RateLimitService.java

```java
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, byte[]> redisTemplate;
    private final RateLimitProperties properties;

    private static final String KEY_PREFIX = "rate_limit:login:";

    public boolean tryConsume(String clientIp) {
        String key = KEY_PREFIX + clientIp;

        // Configuração do bucket
        Bandwidth limit = Bandwidth.builder()
            .capacity(properties.getCapacity())
            .refillGreedy(
                properties.getRefillTokens(),
                Duration.ofMinutes(properties.getRefillDuration())
            )
            .build();

        // Cria ou recupera bucket do Redis
        ProxyManager<String> proxyManager = createProxyManager();
        Bucket bucket = proxyManager.builder()
            .build(key, () -> BucketConfiguration.builder()
                .addLimit(limit)
                .build());

        return bucket.tryConsume(1);
    }

    private ProxyManager<String> createProxyManager() {
        // Integração com Redis via Lettuce
        // (configuração detalhada na implementação)
    }
}
```

### 6. RateLimitFilter.java

```java
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Só aplica rate limit no login POST
        if (!isLoginRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);

        if (!rateLimitService.tryConsume(clientIp)) {
            // Limite excedido - retorna 429
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiResponse<?> error = ApiResponse.error(
                "Muitas tentativas de login. Tente novamente em 15 minutos."
            );
            objectMapper.writeValue(response.getOutputStream(), error);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
            && "/api/auth/login".equals(request.getRequestURI());
    }

    private String extractClientIp(HttpServletRequest request) {
        // Suporta proxies (Cloudflare, nginx)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

### 7. RateLimitExceededException.java

```java
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Rate limit exceeded. Try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
```

### 8. Handler no GlobalExceptionHandler

```java
@ExceptionHandler(RateLimitExceededException.class)
public ResponseEntity<ApiResponse<?>> handleRateLimitExceeded(
        RateLimitExceededException ex) {

    return ResponseEntity
        .status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
        .body(ApiResponse.error(
            "Muitas tentativas de login. Tente novamente em 15 minutos."
        ));
}
```

### 9. Response HTTP 429

Quando limite é excedido:

```json
{
  "success": false,
  "message": "Muitas tentativas de login. Tente novamente em 15 minutos.",
  "data": null
}
```

Headers:
```
Retry-After: 900
X-RateLimit-Remaining: 0
```

## Testes

### Testes Unitários

```java
@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private RedisTemplate<String, byte[]> redisTemplate;

    @InjectMocks
    private RateLimitService rateLimitService;

    @Test
    void shouldAllowRequestsWithinLimit() {
        String ip = "192.168.1.1";

        // Primeiras 5 tentativas devem passar
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimitService.tryConsume(ip)).isTrue();
        }
    }

    @Test
    void shouldBlockRequestsOverLimit() {
        String ip = "192.168.1.1";

        // Consome todas as 5 tentativas
        for (int i = 0; i < 5; i++) {
            rateLimitService.tryConsume(ip);
        }

        // 6ª tentativa deve ser bloqueada
        assertThat(rateLimitService.tryConsume(ip)).isFalse();
    }

    @Test
    void shouldTrackDifferentIpsSeparately() {
        String ip1 = "192.168.1.1";
        String ip2 = "192.168.1.2";

        // Consome limite do IP1
        for (int i = 0; i < 5; i++) {
            rateLimitService.tryConsume(ip1);
        }

        // IP2 ainda deve funcionar
        assertThat(rateLimitService.tryConsume(ip2)).isTrue();
    }
}
```

### Teste de Integração

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class RateLimitIntegrationTest {

    @Container
    static RedisContainer redis = new RedisContainer(
        DockerImageName.parse("redis:7-alpine")
    );

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldReturn429WhenRateLimitExceeded() {
        LoginRequest request = new LoginRequest("test@test.com", "wrong");

        // Faz 5 tentativas (limite)
        for (int i = 0; i < 5; i++) {
            restTemplate.postForEntity("/api/auth/login", request, String.class);
        }

        // 6ª tentativa deve retornar 429
        ResponseEntity<String> response = restTemplate
            .postForEntity("/api/auth/login", request, String.class);

        assertThat(response.getStatusCode())
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().get("Retry-After"))
            .isNotNull();
    }
}
```

## Plano de Implementação

### Arquivos a Criar/Modificar

| Arquivo | Ação | Descrição |
|---------|------|-----------|
| `build.gradle` | Modificar | Adicionar dependências Bucket4j |
| `application.yml` | Modificar | Adicionar configurações rate-limit |
| `RateLimitProperties.java` | Criar | Classe de configuração |
| `RateLimitService.java` | Criar | Lógica de rate limiting com Redis |
| `RateLimitFilter.java` | Criar | Filtro HTTP para interceptar login |
| `RateLimitExceededException.java` | Criar | Exception customizada |
| `GlobalExceptionHandler.java` | Modificar | Adicionar handler para 429 |
| `RateLimitServiceTest.java` | Criar | Testes unitários |
| `RateLimitIntegrationTest.java` | Criar | Testes de integração |

### Ordem de Implementação

1. **Dependências** - Adicionar Bucket4j no build.gradle
2. **Configuração** - Properties e YAML
3. **Service** - Lógica de rate limiting
4. **Filter** - Interceptação de requests
5. **Exception Handler** - Tratamento de erros
6. **Testes** - Unitários e integração
7. **Validação** - Testar manualmente com curl

### Critérios de Sucesso

- [ ] 5 tentativas de login permitidas por IP
- [ ] 6ª tentativa retorna HTTP 429
- [ ] Header `Retry-After` presente na resposta
- [ ] Após 15 minutos, tentativas são liberadas
- [ ] IPs diferentes têm contadores separados
- [ ] Testes passando
