# Architecture

## Project Structure

```
src/main/java/br/com/stockshift/
├── controller/     # REST API endpoints
├── service/        # Business logic
├── repository/     # Spring Data JPA repositories
├── model/
│   ├── entity/     # JPA entities
│   └── enums/      # Enumerations
├── dto/            # Request/Response DTOs
├── security/       # JWT auth, filters, UserPrincipal
├── config/         # App configurations
├── exception/      # Custom exceptions
└── util/           # Utilities (cookies, sanitization)
```

## Layered Architecture

```
Controller → Service → Repository → Entity
     ↓           ↓
    DTO        DTO
```

- Controllers are thin, delegate to services
- Services handle all business logic
- DTOs are separate from entities (request/response packages)

## Multi-Tenancy

- **Strategy:** Discriminator column with `tenant_id` on all tenant-scoped entities
- **Base class:** `TenantAwareEntity` extends `BaseEntity` with automatic tenant isolation
- **Context:** `TenantContext` holds current tenant in thread-local

## Key Entities

| Entity | Purpose |
|--------|---------|
| `BaseEntity` | Common fields (id, createdAt, updatedAt) |
| `TenantAwareEntity` | Extends BaseEntity with tenant_id |
| `User`, `Role`, `Permission` | RBAC system |
| `Product`, `Category`, `Brand` | Product catalog |
| `Warehouse`, `Batch` | Inventory locations and lots |
| `StockMovement`, `StockMovementItem` | Inventory transactions |

## API Conventions

- Base path: `/stockshift`
- All endpoints return `ApiResponse<T>` wrapper
- Pagination via Spring's `Pageable`
- Validation via `@Valid` annotations

### Movement Types

`PURCHASE`, `SALE`, `TRANSFER`, `ADJUSTMENT`, `RETURN`
