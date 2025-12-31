# Design: Sistema de Autenticação com Tokens HTTP-Only

**Data:** 30 de dezembro de 2025  
**Status:** Aprovado  
**Objetivo:** Migrar tokens JWT para cookies HTTP-only, aumentando segurança contra ataques XSS

---

## 1. Visão Geral

### Contexto Atual
- Tokens retornados no corpo JSON
- Frontend armazena em localStorage/sessionStorage (vulnerável a XSS)
- Suporte a Authorization header com Bearer token

### Objetivo
- Mover tokens para cookies HTTP-only (JavaScript não consegue acessar)
- Implementar refresh automático com rotação de refresh tokens
- Manter compatibilidade com header Authorization para testes/ferramentas

---

## 2. Fluxo de Autenticação

### 2.1 Login (`POST /api/auth/login`)
**Request:**
```json
{
  "email": "user@example.com",
  "password": "senha123"
}
```

**Response:** 200 OK
```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "email": "user@example.com",
    "fullName": "João Silva",
    "tokenType": "Bearer",
    "expiresIn": 900000
  }
}
```

**Cookies enviados (Set-Cookie):**
- `accessToken=<jwt>; HttpOnly; Secure=<env>; SameSite=<env>; Path=/api; Max-Age=900`
- `refreshToken=<uuid>; HttpOnly; Secure=<env>; SameSite=<env>; Path=/api; Max-Age=604800`

### 2.2 Requisições Autenticadas
Browser envia cookies automaticamente. Backend valida no `JwtAuthenticationFilter`:

1. Tenta ler do cookie `accessToken`
2. Se não existe, tenta do header `Authorization: Bearer <token>`
3. Se válido, processa a requisição
4. Se inválido/expirado, retorna 401

### 2.3 Refresh Token (`POST /api/auth/refresh`)
**Request:** vazio (tokens lidos dos cookies)
```
POST /api/auth/refresh
```

**Response:** 200 OK
```json
{
  "success": true,
  "data": null
}
```

**Cookies enviados (Set-Cookie):**
- Novo `accessToken` (rotacionado)
- Novo `refreshToken` (rotacionado - refresh token deslizante)

**Lógica no backend:**
1. Ler `refreshToken` do cookie da requisição
2. Validar expiração e existência no banco
3. Gerar novo access token
4. Gerar novo refresh token (deletar antigo, criar novo)
5. Enviar ambos os cookies na resposta

### 2.4 Logout (`POST /api/auth/logout`)
**Request:** vazio

**Response:** 200 OK

**Cookies enviados (Set-Cookie):**
- `accessToken=; Max-Age=0` (remover)
- `refreshToken=; Max-Age=0` (remover)

**Lógica:**
1. Ler `refreshToken` do cookie
2. Deletar do banco de dados
3. Limpar cookies na response

---

## 3. Configuração de Cookies

### 3.1 Propriedades (application.yml)

```yaml
jwt:
  secret: ${JWT_SECRET}
  access-expiration: 900000      # 15 minutos
  refresh-expiration: 604800000  # 7 dias
  cookie:
    secure: false                # true em prod
    same-site: Lax              # None em prod (CORS)
    domain: localhost            # .example.com em prod
    path: /api
    http-only: true             # sempre true
```

### 3.2 Atributos dos Cookies

| Atributo | Desenvolvimento | Produção | Motivo |
|----------|-----------------|----------|--------|
| **Secure** | false | true | HTTPS obrigatório em prod |
| **SameSite** | Lax | None | CORS cross-origin em prod |
| **Domain** | localhost | .example.com | Subdomínios em prod |
| **HttpOnly** | true | true | Segurança contra XSS |
| **Path** | /api | /api | Apenas API recebe cookies |
| **Max-Age** | conforme token | conforme token | Alinhado com expiração |

### 3.3 Classe CookieProperties

```java
@ConfigurationProperties(prefix = "jwt.cookie")
@Data
public class CookieProperties {
    private boolean secure;
    private String sameSite;      // Lax, Strict, None
    private String domain;
    private String path;
    private boolean httpOnly;
}
```

### 3.4 Classe CookieUtil (Helper)

```java
@Component
@RequiredArgsConstructor
public class CookieUtil {
    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;
    
    public void addAccessTokenCookie(
        HttpServletResponse response, 
        String token) {
        // Criar cookie com propriedades
    }
    
    public void addRefreshTokenCookie(
        HttpServletResponse response, 
        String token) {
        // Criar cookie com propriedades
    }
    
    public void removeAccessTokenCookie(HttpServletResponse response) {
        // Max-Age=0
    }
    
    public void removeRefreshTokenCookie(HttpServletResponse response) {
        // Max-Age=0
    }
}
```

---

## 4. Modificações no AuthController

### 4.1 Login (modificado)
```java
@PostMapping("/login")
@Operation(summary = "Login", description = "Authenticate and set HTTP-only cookies")
public ResponseEntity<ApiResponse<LoginResponse>> login(
    @Valid @RequestBody LoginRequest request,
    HttpServletResponse response) {
    
    LoginResponse loginResponse = authService.login(request);
    
    // Adicionar cookies
    cookieUtil.addAccessTokenCookie(response, loginResponse.getAccessToken());
    cookieUtil.addRefreshTokenCookie(response, loginResponse.getRefreshToken());
    
    // Remover tokens da resposta JSON
    loginResponse.setAccessToken(null);
    loginResponse.setRefreshToken(null);
    
    return ResponseEntity.ok(ApiResponse.success(loginResponse));
}
```

### 4.2 Refresh (novo comportamento)
```java
@PostMapping("/refresh")
@Operation(summary = "Refresh Token", description = "Generate new tokens from refresh token cookie")
public ResponseEntity<ApiResponse<Void>> refresh(
    HttpServletRequest request,
    HttpServletResponse response) {
    
    // Refresh token lido automaticamente do cookie no serviço
    RefreshTokenResponse tokens = authService.refresh(request);
    
    // Adicionar novos cookies (rotacionados)
    cookieUtil.addAccessTokenCookie(response, tokens.getAccessToken());
    cookieUtil.addRefreshTokenCookie(response, tokens.getNewRefreshToken());
    
    return ResponseEntity.ok(ApiResponse.success("Tokens refreshed"));
}
```

### 4.3 Logout (modificado)
```java
@PostMapping("/logout")
@Operation(summary = "Logout", description = "Revoke refresh token and clear cookies")
public ResponseEntity<ApiResponse<Void>> logout(
    HttpServletRequest request,
    HttpServletResponse response) {
    
    String refreshToken = getRefreshTokenFromCookie(request);
    if (refreshToken != null) {
        authService.logout(refreshToken);
    }
    
    // Remover cookies
    cookieUtil.removeAccessTokenCookie(response);
    cookieUtil.removeRefreshTokenCookie(response);
    
    return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
}

private String getRefreshTokenFromCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
        for (Cookie cookie : cookies) {
            if ("refreshToken".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
    }
    return null;
}
```

---

## 5. Modificações no JwtAuthenticationFilter

### 5.1 Leitura Dual (Cookies + Header)

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String jwt = getJwtFromRequest(request);
            
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                UUID userId = tokenProvider.getUserIdFromToken(jwt);
                UUID tenantId = tokenProvider.getTenantIdFromToken(jwt);
                
                TenantContext.setTenantId(tenantId);
                
                UserDetails userDetails = userDetailsService.loadUserById(userId.toString());
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        // Prioridade 1: Cookie accessToken
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        // Prioridade 2: Authorization header (fallback)
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        return null;
    }
}
```

---

## 6. Modificações no AuthService

### 6.1 Login (sem mudanças na lógica, apenas DTOs)
Mantém a lógica atual, mas `AuthController` remove tokens da resposta.

### 6.2 Refresh com Rotação
```java
@Transactional
public RefreshTokenResponse refresh(HttpServletRequest request) {
    // Ler refresh token do cookie
    String refreshTokenValue = getRefreshTokenFromCookie(request);
    if (refreshTokenValue == null) {
        throw new UnauthorizedException("Refresh token not found");
    }
    
    // Validar
    RefreshToken refreshToken = refreshTokenService.validateRefreshToken(refreshTokenValue);
    User user = refreshToken.getUser();
    
    if (user == null || !user.getIsActive()) {
        throw new UnauthorizedException("User not found or inactive");
    }
    
    // Gerar novo access token
    String accessToken = jwtTokenProvider.generateAccessToken(
        user.getId(),
        user.getTenantId(),
        user.getEmail()
    );
    
    // Rotacionar refresh token (deletar antigo, criar novo)
    RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user);
    
    return RefreshTokenResponse.builder()
        .accessToken(accessToken)
        .newRefreshToken(newRefreshToken.getToken())
        .tokenType("Bearer")
        .expiresIn(jwtProperties.getAccessExpiration())
        .build();
}

@Transactional
public void logout(String refreshTokenValue) {
    refreshTokenService.revokeRefreshToken(refreshTokenValue);
}

private String getRefreshTokenFromCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
        for (Cookie cookie : cookies) {
            if ("refreshToken".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
    }
    return null;
}
```

---

## 7. Exceções Novas

```java
public class RefreshTokenExpiredException extends UnauthorizedException {
    public RefreshTokenExpiredException(String message) {
        super(message);
    }
}

public class InvalidRefreshTokenException extends UnauthorizedException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
```

---

## 8. Tratamento de Erros

| Cenário | Status | Erro | Ação |
|---------|--------|------|------|
| Refresh token expirado | 401 | `REFRESH_TOKEN_EXPIRED` | Frontend redireciona para login |
| Refresh token inválido | 401 | `INVALID_REFRESH_TOKEN` | Frontend redireciona para login |
| Usuário inativo | 401 | `UNAUTHORIZED` | Frontend redireciona para login |
| Cookie corrompido | 401 | `UNAUTHORIZED` | Filtro ignora, endpoint retorna 401 |
| Sem cookies/header | 401 | `UNAUTHORIZED` | Endpoint protegido retorna 401 |

---

## 9. CORS e Credentials

### 9.1 Configuração SecurityConfig

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://app.example.com"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);  // ESSENCIAL para cookies
    configuration.setMaxAge(3600L);
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
}
```

### 9.2 Frontend - Requisições com Credentials

```javascript
// Exemplo com fetch
fetch('/api/auth/refresh', {
    method: 'POST',
    credentials: 'include',  // ESSENCIAL - envia cookies
    headers: { 'Content-Type': 'application/json' }
});
```

---

## 10. DTOs Atualizados

### 10.1 LoginResponse
```java
@Data
@Builder
public class LoginResponse {
    private UUID userId;
    private String email;
    private String fullName;
    private String tokenType;
    private Long expiresIn;
    private String accessToken;      // null (removido antes de enviar)
    private String refreshToken;     // null (removido antes de enviar)
}
```

### 10.2 RefreshTokenResponse
```java
@Data
@Builder
public class RefreshTokenResponse {
    private String accessToken;
    private String newRefreshToken;
    private String tokenType;
    private Long expiresIn;
}
```

---

## 11. Testes Integrados

### 11.1 Testes de Login
- Verificar que resposta JSON não contém tokens
- Verificar que cookies `accessToken` e `refreshToken` são enviados
- Verificar atributos do cookie (HttpOnly, Secure, SameSite, Path)

### 11.2 Testes de Refresh
- Fazer login, obter cookies
- Fazer requisição para `/api/auth/refresh` sem corpo
- Verificar que novos cookies são retornados
- Verificar que antigo refresh token é invalidado

### 11.3 Testes de Logout
- Fazer login
- Chamar `/api/auth/logout`
- Verificar que cookies são removidos (Max-Age=0)

### 11.4 Testes de Leitura Dual
- Teste 1: Requisição autenticada com cookie → sucesso
- Teste 2: Requisição autenticada com header Authorization → sucesso
- Teste 3: Requisição com ambos (prioridade cookie) → sucesso

---

## 12. Impacto no Código Existente

### Modificações Necessárias
1. ✅ AuthController (3 endpoints)
2. ✅ AuthService (métodos refresh e logout)
3. ✅ JwtAuthenticationFilter (leitura dual)
4. ✅ Criar CookieProperties e CookieUtil
5. ✅ Criar exceções novas
6. ✅ Atualizar application.yml
7. ✅ Atualizar DTOs (remover tokens de Login/Refresh responses)
8. ✅ Testes integrados

### Sem Impacto
- TenantService (register continua igual)
- JwtTokenProvider (gera tokens igual)
- RefreshTokenService (valida/cria/revoga igual)
- Outras controllers (apenas usam autenticação via filtro)

---

## 13. Segurança

### Proteções Implementadas
- ✅ **XSS:** Tokens em HTTP-only, JavaScript não consegue acessar
- ✅ **CSRF:** SameSite=Strict/None previne requisições cross-site maliciosas
- ✅ **HTTPS:** Secure=true em produção
- ✅ **Rotação:** Refresh token deslizante invalida tokens antigos
- ✅ **Expiração:** Access token com vida curta (15 min)

### Configurações por Ambiente
- **Dev:** Sem HTTPS, SameSite=Lax, permite testes
- **Prod:** HTTPS obrigatório, SameSite=None, cross-origin seguro

---

## 14. Checklist de Implementação

- [ ] Criar CookieProperties e ler configurações
- [ ] Criar CookieUtil com helpers para cookies
- [ ] Criar exceções (RefreshTokenExpiredException, InvalidRefreshTokenException)
- [ ] Atualizar application.yml com cookie properties
- [ ] Modificar JwtAuthenticationFilter (leitura dual)
- [ ] Modificar AuthService (refresh com rotação)
- [ ] Modificar AuthController (todos 3 endpoints)
- [ ] Atualizar DTOs (remover tokens de responses)
- [ ] Atualizar SecurityConfig (CORS com credentials)
- [ ] Escrever testes integrados (4 suites)
- [ ] Testar em dev e prod
- [ ] Documentar mudanças para frontend (credentials: 'include')

