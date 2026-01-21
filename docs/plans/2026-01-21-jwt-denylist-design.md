# JWT Token Denylist Design

**Data:** 2026-01-21
**Status:** Aprovado
**Vulnerabilidade:** vuln-0001 (Insecure JWT Logout Implementation)

## Problema

Access tokens JWT permanecem válidos após logout. Um atacante com acesso a um token pode continuar usando-o até a expiração natural (15 minutos), mesmo após o usuário ter feito logout.

## Solução

Implementar um denylist de tokens usando Redis. Ao fazer logout, o `jti` (JWT ID) do token é adicionado ao Redis com TTL igual ao tempo restante do token.

## Arquitetura

```
┌─────────────┐     POST /logout      ┌─────────────┐
│   Cliente   │ ───────────────────▶  │AuthController│
└─────────────┘                       └──────┬──────┘
                                             │
                                             ▼
                                      ┌─────────────┐
                                      │ AuthService │
                                      └──────┬──────┘
                                             │ adiciona jti ao denylist
                                             ▼
┌─────────────┐                       ┌─────────────┐
│   Request   │ ──▶ JwtAuthFilter ──▶ │    Redis    │
│  Protegido  │     verifica denylist │  (denylist) │
└─────────────┘                       └─────────────┘
```

## Componentes

### Novos Arquivos

| Arquivo | Responsabilidade |
|---------|------------------|
| `TokenDenylistService.java` | Interface para operações de denylist |
| `RedisTokenDenylistService.java` | Implementação Redis do denylist |
| `RedisConfig.java` | Configuração do Redis client |

### Arquivos Modificados

| Arquivo | Alteração |
|---------|-----------|
| `build.gradle` | Adicionar `spring-boot-starter-data-redis` |
| `application.yml` | Configuração de conexão Redis |
| `JwtTokenProvider.java` | Adicionar `jti` na geração e extração |
| `JwtAuthenticationFilter.java` | Verificar denylist antes de aceitar token |
| `AuthService.java` | Chamar denylist no logout |

## Estrutura de Dados (Redis)

```
Key:    "token:denylist:{jti}"
Value:  "1"
TTL:    tempo restante até expiração do token
```

## Detalhes de Implementação

### 1. Geração do JWT com `jti`

```java
public String generateAccessToken(UUID userId, UUID tenantId, String email) {
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

### 2. Interface do Denylist

```java
public interface TokenDenylistService {
    void addToBlacklist(String jti, long ttlMillis);
    boolean isBlacklisted(String jti);
}
```

### 3. Verificação no Filtro

```java
if (jwtTokenProvider.validateToken(jwt)) {
    String jti = jwtTokenProvider.getJtiFromToken(jwt);

    if (tokenDenylistService.isBlacklisted(jti)) {
        log.warn("Token revogado: {}", jti);
        return;  // Não autentica
    }
    // ... continua autenticação normal
}
```

### 4. Logout

```java
public void logout(String accessToken, String refreshToken) {
    // Existente: invalida refresh token no banco
    refreshTokenRepository.deleteByToken(refreshToken);

    // Novo: adiciona access token ao denylist
    String jti = jwtTokenProvider.getJtiFromToken(accessToken);
    long ttl = jwtTokenProvider.getRemainingTtl(accessToken);
    tokenDenylistService.addToBlacklist(jti, ttl);
}
```

## Configuração

### build.gradle

```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

### application.yml

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
```

## Resiliência

**Estratégia: Fail-open**

Se o Redis estiver indisponível:
- Logout: Loga erro, continua (refresh token já invalidado no banco)
- Validação: Loga warning, aceita token (prioriza disponibilidade)

## Testes

| Cenário | Esperado |
|---------|----------|
| Login gera token com `jti` válido | Token contém claim `jti` como UUID |
| Logout adiciona `jti` ao Redis | Chave existe com TTL correto |
| Request com token revogado | 401 Unauthorized |
| Request com token válido não revogado | 200 OK |
| Redis indisponível no logout | Loga erro, continua |
| Redis indisponível na validação | Loga warning, aceita token |
| Token expirado no Redis | Chave removida automaticamente pelo TTL |
