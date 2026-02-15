# StockShift Backend

API REST multi-tenant para gestao de estoque com autenticacao JWT, RBAC e controle por warehouse.

## Estado Atual (2026-02-15)

- CI estabilizada para suite de testes.
- Fluxo de transfer pronto para producao (constraints, permissao por acao e tratamento de conflitos).
- Tenant/Warehouse context limpo a cada request no filtro JWT.
- Backlog prioritario pendente: testes de erro, multi-tenant e concorrencia.

## Stack

- Java 17
- Spring Boot 4.0.1
- Spring Security + JWT
- PostgreSQL 16
- Flyway
- Redis (denylist de tokens e rate limiting)
- OpenAPI/Swagger
- Testcontainers + JUnit 5

## Executando Localmente

### 1. Subir infraestrutura

```bash
docker-compose up -d postgres redis
```

Opcional (pgAdmin):

```bash
docker-compose --profile tools up -d
```

### 2. Rodar a aplicacao

```bash
./gradlew bootRun
```

A aplicacao sobe com `server.servlet.context-path=/stockshift`.

### 3. Swagger

- `http://localhost:8080/stockshift/swagger-ui/index.html`

## Banco de Dados e Migracoes

O projeto esta consolidado em uma migracao baseline:

- `src/main/resources/db/migration/V1__initial_schema.sql`

Para aplicar migracoes:

```bash
./gradlew flywayMigrate
```

## Configuracao (variaveis principais)

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=stockshift
DB_USER=postgres
DB_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT
JWT_SECRET=dev-secret-key-change-in-production-must-be-at-least-256-bits-long
JWT_ACCESS_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000
JWT_COOKIE_SECURE=false
JWT_COOKIE_SAME_SITE=Lax
JWT_COOKIE_DOMAIN=

# CORS
ALLOWED_ORIGINS=http://localhost:3000
```

## Testes

```bash
# Suite completa
./gradlew test --no-daemon

# Apenas integration tests
./gradlew test --no-daemon --tests "*IntegrationTest"

# Relatorio JaCoCo
./gradlew test jacocoTestReport
```

## Documentacao Funcional

- Endpoints de auth: `docs/endpoints/auth.md`
- Endpoints de produtos: `docs/endpoints/products.md`
- Endpoints de batches: `docs/endpoints/batches.md`
- Endpoints de warehouses: `docs/endpoints/warehouses.md`
- Endpoints de transfer: `docs/endpoints/transfer.md`
- Cobertura de testes de integracao: `docs/testing/INTEGRATION_TESTS_COVERAGE.md`

## Principais Modulos

- `src/main/java/br/com/stockshift/controller`: endpoints REST
- `src/main/java/br/com/stockshift/service`: regras de negocio
- `src/main/java/br/com/stockshift/security`: JWT, contexto de tenant/warehouse e autorizacao
- `src/main/java/br/com/stockshift/repository`: acesso a dados
- `src/main/resources/db/migration`: schema Flyway

## Observacoes de Seguranca

- Contextos `TenantContext` e `WarehouseContext` sao limpos no fim de cada request.
- Endpoints sensiveis usam `@PreAuthorize` com authorities por recurso/acao (ex.: `TRANSFER_READ`, `TRANSFER_EXECUTE`).
- Token JWT pode vir por cookie `accessToken` ou header `Authorization: Bearer ...`.
