<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://i.imgur.com/cxBS4yQ.png">
    <source media="(prefers-color-scheme: light)" srcset="https://i.imgur.com/N6DwsGh.png">
    <img src="https://i.imgur.com/N6DwsGh.png" alt="StockShift" width="360">
  </picture>
</p>

# StockShift Backend

StockShift Backend is the REST API that powers [StockShift](https://github.com/lexmarcos/StockShift), a multi-tenant inventory and warehouse management application. It handles authentication, tenant isolation, warehouse-scoped authorization, product catalog data, batches, stock movements, transfers, sales, reporting, file uploads, payment webhooks, and audit trails.

The application is served under the `/stockshift` context path. In local development, the API base URL is usually `http://localhost:8080/stockshift`.

## What It Does

- Manages tenants, users, roles, permissions, and warehouse-scoped access.
- Stores products, brands, categories, batches, and stock quantities per warehouse.
- Records stock movements and keeps an inventory ledger for traceability.
- Supports warehouse-to-warehouse transfers with execution, validation, discrepancy reports, and validation logs.
- Exposes sales workflows, sales dashboards, and InfinitePay payment confirmation/webhook handling.
- Generates operational reports for dashboards, stock, low-stock products, expiring batches, and movement trends.
- Uploads product images and company logos to S3-compatible storage.
- Can analyze product images with OpenAI to suggest product metadata.
- Writes audit events and exports audit data as CSV or XLSX.

## Architecture

This backend follows a conventional Spring Boot layered architecture:

```text
HTTP clients
  -> controllers
  -> services
  -> repositories
  -> PostgreSQL

Cross-cutting layers:
  security filters -> JWT, tenant context, warehouse context, audit context
  migrations       -> Flyway SQL scripts
  cache/ephemeral  -> Redis token denylist and rate limiting
  adapters         -> S3-compatible storage, InfinitePay, OpenAI
  jobs             -> reconciliation and temporary upload cleanup
```

### Request Flow

1. A request reaches the API under `/stockshift/api/**`.
2. `SecurityConfig` applies stateless Spring Security rules.
3. `JwtAuthenticationFilter` reads the JWT from the `accessToken` cookie first, then from the `Authorization: Bearer ...` header.
4. The filter validates the token, checks the Redis-backed denylist, verifies the user and tenant, then sets `TenantContext` and `WarehouseContext`.
5. Controllers validate and map HTTP input into DTOs.
6. Services enforce business rules, permissions, tenant boundaries, warehouse boundaries, and transactional behavior.
7. Repositories persist tenant-scoped entities through Spring Data JPA.
8. Flyway owns database schema evolution.
9. The request context is cleared after each request to avoid tenant or warehouse leakage.

### Main Modules

| Path | Responsibility |
| --- | --- |
| `src/main/java/br/com/stockshift/controller` | REST endpoints and HTTP response shaping |
| `src/main/java/br/com/stockshift/service` | Business rules for auth, catalog, inventory, transfers, reports, sales, storage, audit, and integrations |
| `src/main/java/br/com/stockshift/security` | JWT auth, user principal, tenant context, warehouse context, permission guards, and rate limiting |
| `src/main/java/br/com/stockshift/model/entity` | JPA entities and tenant-aware base models |
| `src/main/java/br/com/stockshift/model/enums` | Domain enums for permissions, movements, transfers, sales, payments, and validations |
| `src/main/java/br/com/stockshift/repository` | Spring Data JPA repositories |
| `src/main/java/br/com/stockshift/dto` | Request and response contracts |
| `src/main/java/br/com/stockshift/config` | Spring, security, CORS, Redis, storage, OpenAPI, and client configuration |
| `src/main/resources/db/migration` | Flyway migrations |
| `docs/endpoints` | Functional endpoint documentation |
| `src/test/java/br/com/stockshift` | Unit, integration, security, and regression tests |

## Technology Stack

- Java 17
- Spring Boot 4.0.1
- Spring Web
- Spring Security with stateless JWT authentication
- Spring Data JPA and Hibernate
- PostgreSQL 16
- Flyway
- Redis
- Bucket4j for login rate limiting
- OpenAPI/Swagger through Springdoc
- AWS SDK S3 client for S3-compatible storage, including Supabase Storage
- OpenAI API integration for product image classification
- InfinitePay checkout and webhook integration
- Apache POI and OpenPDF for report exports
- JUnit 5, Spring Security Test, Testcontainers, MockWebServer, and JaCoCo
- Docker and Docker Compose for local infrastructure

## Requirements

- Java 17
- Docker and Docker Compose
- A working Docker daemon for local services and Testcontainers
- The Gradle wrapper included in this repository

## Running Locally

Create a local development configuration from the example file:

```bash
cp src/main/resources/application-dev.example.yml src/main/resources/application-dev.yml
```

Start PostgreSQL and Redis:

```bash
docker compose -f docker-compose.local.yml up -d postgres redis
```

Run the application:

```bash
./gradlew bootRun
```

The API will be available at:

- API base URL: `http://localhost:8080/stockshift`
- Swagger UI: `http://localhost:8080/stockshift/swagger-ui/index.html`
- Health check: `http://localhost:8080/stockshift/actuator/health`

To start pgAdmin as well:

```bash
docker compose -f docker-compose.local.yml --profile tools up -d
```

pgAdmin runs at `http://localhost:5050` with the local defaults from `docker-compose.local.yml`.

## Configuration

The default active profile is `dev`. Local development settings should live in `src/main/resources/application-dev.yml`, which is intentionally derived from `application-dev.example.yml`.

Important environment variables:

| Variable | Purpose | Local default |
| --- | --- | --- |
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | PostgreSQL database | `stockshift` |
| `DB_USER` | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | `postgres` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis password | empty |
| `JWT_SECRET` | HMAC secret used to sign JWTs | dev-only example value |
| `JWT_ACCESS_EXPIRATION` | Access token lifetime in milliseconds | `900000` |
| `JWT_REFRESH_EXPIRATION` | Refresh token lifetime in milliseconds | `604800000` |
| `ALLOWED_ORIGINS` | CORS allowlist | local frontend and proxy origins |
| `FRONTEND_URL` | Frontend URL used in redirects | set per environment |
| `API_BASE_URL` | Public API base URL | set per environment |
| `STORAGE_ENDPOINT` | S3-compatible storage endpoint | empty |
| `STORAGE_ACCESS_KEY` | Storage access key | empty |
| `STORAGE_SECRET_KEY` | Storage secret key | empty |
| `STORAGE_BUCKET_NAME` | Storage bucket name | `stockshift` |
| `STORAGE_PUBLIC_URL` | Public URL used to serve uploaded files | empty |
| `HCAPTCHA_SECRET_KEY` | hCaptcha verification secret | empty |
| `OPENAI_API_KEY` | OpenAI API key for image classification | empty |
| `OPENAI_API_URL` | OpenAI API base URL | `https://api.openai.com` |
| `OPENAI_MODEL` | OpenAI model used by the classifier | `gpt-4.1-nano` |

Production uses `SPRING_PROFILES_ACTIVE=prod`, requires real secrets, enables secure JWT cookies, disables Swagger/OpenAPI, and expects all required database, storage, hCaptcha, and OpenAI variables to be provided by the runtime environment.

## Database And Migrations

Flyway runs automatically on startup and loads migrations from:

```text
src/main/resources/db/migration
```

The schema is tenant-aware. Core domain tables include tenants, warehouses, products, categories, brands, batches, roles, permissions, users, stock movements, inventory ledger entries, transfers, sales, audit events, uploaded product images, and product prompts.

To run migrations explicitly:

```bash
./gradlew flywayMigrate
```

## Authentication And Authorization

The API is stateless. Authentication uses signed JWTs and supports:

- `accessToken` HTTP-only cookie
- `Authorization: Bearer <token>` header
- Redis-backed token denylist for logout and token revocation
- Login rate limiting through Bucket4j and Redis
- Method-level authorization through `@PreAuthorize`
- Permission checks such as `products:read`, `batches:create`, `transfers:validate`, and `reports:read`
- Warehouse-scoped access for operations that must stay inside the selected warehouse

Public endpoints are limited to Swagger in development, health checks, auth login/refresh/register, and the tokenized InfinitePay webhook endpoint.

## API Documentation

When running with the development profile, Swagger UI is available at:

```text
http://localhost:8080/stockshift/swagger-ui/index.html
```

Endpoint documentation is also kept in the repository:

- [Authentication](docs/endpoints/auth.md)
- [Brands](docs/endpoints/brands.md)
- [Categories](docs/endpoints/categories.md)
- [Products](docs/endpoints/products.md)
- [Batches](docs/endpoints/batches.md)
- [Warehouses](docs/endpoints/warehouses.md)
- [Users](docs/endpoints/users.md)
- [Stock movements](docs/endpoints/stock-movements.md)
- [Transfers](docs/endpoints/transfer.md)
- [Reports](docs/endpoints/reports.md)
- [Frontend authentication guide](docs/FRONTEND_AUTH_GUIDE.md)

## Tests And Quality

Run the full test suite:

```bash
./gradlew test
```

Run the complete quality gate used by CI:

```bash
./gradlew check
```

Generate the JaCoCo report:

```bash
./gradlew test jacocoTestReport
```

The CI workflow runs `./gradlew check --no-daemon` on pushes and pull requests to `main`.

## Docker

Build the application image:

```bash
docker build -t stockshift-backend .
```

Run local infrastructure:

```bash
docker compose -f docker-compose.local.yml up -d postgres redis
```

The `Dockerfile` builds the Spring Boot jar with the Gradle wrapper, then runs it on Eclipse Temurin 17 JRE Alpine as a non-root `stockshift` user.

## Production Notes

- Use `SPRING_PROFILES_ACTIVE=prod`.
- Provide a strong `JWT_SECRET`; do not use the development default.
- Serve the API behind HTTPS because production cookies are secure.
- Configure `ALLOWED_ORIGINS` to the deployed frontend origin.
- Provide production PostgreSQL, Redis, storage, hCaptcha, OpenAI, and payment-related settings.
- Keep Flyway migrations immutable after release.
- Swagger and OpenAPI are disabled by the production profile.

## License

This project is licensed under the [PolyForm Internal Use License 1.0.0](LICENSE).
