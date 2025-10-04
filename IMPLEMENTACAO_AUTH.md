# Sistema de Autenticação JWT - Implementação Completa

## ✅ Funcionalidades Implementadas

### 1. Endpoints de Autenticação
- ✅ **POST /api/v1/auth/login** - Login com username/password
- ✅ **POST /api/v1/auth/refresh** - Refresh do access token
- ✅ **POST /api/v1/auth/logout** - Revogação do refresh token

### 2. Segurança JWT
- ✅ Geração de Access Token (válido por 1 hora)
- ✅ Geração de Refresh Token (válido por 24 horas)
- ✅ Validação de tokens
- ✅ Filtro JWT para autenticação em requisições
- ✅ Tokens armazenados com hash SHA-256

### 3. Entidades do Domínio
- ✅ **User** - Entidade de usuário com UserDetails
- ✅ **RefreshToken** - Entidade para gerenciar refresh tokens
- ✅ **UserRole** - Enum com roles (ADMIN, MANAGER, SELLER)

### 4. Arquitetura em Camadas (DDD-lite)
```
api/
  ├── controller/
  │   ├── AuthController.java
  │   └── TestController.java
  ├── dto/auth/
  │   ├── LoginRequest.java
  │   ├── LoginResponse.java
  │   ├── RefreshTokenRequest.java
  │   └── LogoutRequest.java
  └── exception/
      ├── ErrorResponse.java
      └── GlobalExceptionHandler.java

application/
  └── service/
      ├── AuthService.java
      ├── CustomUserDetailsService.java
      └── RefreshTokenService.java

domain/
  └── user/
      ├── User.java
      ├── UserRole.java
      └── RefreshToken.java

infrastructure/
  ├── repository/
  │   ├── UserRepository.java
  │   └── RefreshTokenRepository.java
  ├── security/
  │   ├── JwtTokenProvider.java
  │   ├── JwtAuthenticationFilter.java
  │   └── SecurityConfig.java
  └── config/
      └── DataInitializer.java
```

### 5. Validações e Tratamento de Erros
- ✅ Validação de DTOs com Jakarta Validation
- ✅ Global Exception Handler (RFC 7807)
- ✅ Tratamento de credenciais inválidas
- ✅ Tratamento de tokens expirados/revogados
- ✅ Mensagens de erro padronizadas

### 6. Recursos Adicionais
- ✅ Inicializador de dados (3 usuários padrão)
- ✅ Endpoints de teste com diferentes níveis de autorização
- ✅ Script shell para testes automatizados
- ✅ Coleção Postman/Insomnia
- ✅ Documentação completa

## 🚀 Como Usar

### 1. Configurar o Banco de Dados
```sql
CREATE DATABASE stockshift;
CREATE USER stockshift WITH PASSWORD 'stockshift';
GRANT ALL PRIVILEGES ON DATABASE stockshift TO stockshift;
```

### 2. Executar a Aplicação
```bash
./gradlew bootRun
```

### 3. Testar os Endpoints

#### Opção 1: Script Automatizado
```bash
./test-auth.sh
```

#### Opção 2: cURL Manual
```bash
# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Usar o access token retornado
export TOKEN="seu_access_token_aqui"

# Endpoint autenticado
curl -X GET http://localhost:8080/api/v1/test/authenticated \
  -H "Authorization: Bearer $TOKEN"
```

#### Opção 3: Postman/Insomnia
Importe o arquivo `postman-collection.json`

## 👥 Usuários Padrão

| Username | Password    | Role    |
|----------|-------------|---------|
| admin    | admin123    | ADMIN   |
| manager  | manager123  | MANAGER |
| seller   | seller123   | SELLER  |

## 🔐 Fluxo de Autenticação

1. **Login**: Cliente envia username/password → Recebe access token + refresh token
2. **Requisições**: Cliente usa access token no header `Authorization: Bearer {token}`
3. **Refresh**: Quando access token expira, cliente usa refresh token para obter novo access token
4. **Logout**: Cliente envia refresh token para revogá-lo

## 📝 Endpoints de Teste

| Endpoint | Acesso | Descrição |
|----------|--------|-----------|
| GET /api/v1/test/public | Público | Sem autenticação |
| GET /api/v1/test/authenticated | Autenticado | Qualquer usuário logado |
| GET /api/v1/test/admin | ADMIN | Apenas ADMIN |
| GET /api/v1/test/manager | ADMIN, MANAGER | ADMIN ou MANAGER |

## ⚠️ Segurança - Antes de Produção

1. ✅ Alterar `jwt.secret` para um valor seguro e aleatório
2. ✅ Usar variáveis de ambiente para credenciais
3. ✅ Configurar CORS adequadamente
4. ✅ Habilitar HTTPS
5. ✅ Implementar rate limiting
6. ✅ Adicionar logs de auditoria
7. ✅ Considerar usar Redis para refresh tokens

## 📚 Dependências Adicionadas

```gradle
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

## 🎯 Próximos Passos Sugeridos

1. Adicionar Flyway para migrations
2. Implementar warehouse scoping para SELLER
3. Adicionar endpoint de registro de usuários
4. Implementar recuperação de senha
5. Adicionar logs estruturados
6. Implementar testes unitários e de integração
7. Adicionar OpenAPI/Swagger documentation
8. Implementar refresh token rotation
