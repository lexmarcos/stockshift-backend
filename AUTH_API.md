# Authentication API - Stockshift Backend

## Endpoints de Autenticação

Base URL: `http://localhost:8080/api/v1/auth`

### 1. Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "username": "admin",
  "role": "ADMIN"
}
```

### 2. Refresh Token
```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "username": "admin",
  "role": "ADMIN"
}
```

### 3. Logout
```http
POST /api/v1/auth/logout
Content-Type: application/json

{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:** 204 No Content

## Usuários Padrão

O sistema cria automaticamente 3 usuários na primeira execução:

1. **Admin**
   - Username: `admin`
   - Password: `admin123`
   - Role: `ADMIN`

2. **Manager**
   - Username: `manager`
   - Password: `manager123`
   - Role: `MANAGER`

3. **Seller**
   - Username: `seller`
   - Password: `seller123`
   - Role: `SELLER`

## Usando o Access Token

Após o login, use o `accessToken` no header Authorization:

```http
GET /api/v1/some-protected-endpoint
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## Testando com cURL

### Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

### Refresh Token
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN_HERE"
  }'
```

### Logout
```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN_HERE"
  }'
```

## Configuração do Banco de Dados

Certifique-se de ter o PostgreSQL rodando com um banco chamado `stockshift`:

```sql
CREATE DATABASE stockshift;
CREATE USER stockshift WITH PASSWORD 'stockshift';
GRANT ALL PRIVILEGES ON DATABASE stockshift TO stockshift;
```

## Executando a Aplicação

```bash
./gradlew bootRun
```

## Tokens

- **Access Token**: Válido por 1 hora (3600 segundos)
- **Refresh Token**: Válido por 24 horas (86400 segundos)

## Segurança

⚠️ **IMPORTANTE**: Antes de colocar em produção:

1. Altere o `jwt.secret` no `application.properties` para um valor seguro e aleatório de pelo menos 256 bits
2. Configure CORS adequadamente
3. Use HTTPS
4. Considere usar variáveis de ambiente para credenciais sensíveis
