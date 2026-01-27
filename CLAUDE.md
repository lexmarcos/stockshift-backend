# StockShift Backend

Multi-tenant stock management system for small businesses to manage inventory across multiple warehouses.

## Commands

```bash
./gradlew build      # Build
./gradlew test       # Run tests
./gradlew bootRun    # Run application
docker-compose up -d # Start Postgres + Redis
```

## Key Patterns

- **Multi-tenancy:** All tenant-scoped entities extend `TenantAwareEntity` with `tenant_id`
- **API wrapper:** All endpoints return `ApiResponse<T>` at base path `/stockshift`
- **XSS prevention:** Use `SanitizationUtil` for user input

## Guidelines

- [Architecture](.claude/architecture.md) - Project structure, entities, layered design
- [Security](.claude/security.md) - JWT, permissions, rate limiting
- [Database](.claude/database.md) - Migrations, table conventions
- [Testing](.claude/testing.md) - Testcontainers, integration tests
- [Configuration](.claude/configuration.md) - Environment variables, local setup
