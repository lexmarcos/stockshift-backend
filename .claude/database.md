# Database

## Migrations

- Location: `src/main/resources/db/migration/`
- Naming: `V{number}__{description}.sql` (Flyway convention)

### Example

```
V1__create_users_table.sql
V2__add_products_table.sql
V19__create_sales_tables.sql
```

## Table Conventions

| Convention | Description |
|------------|-------------|
| Multi-tenant | Tables have `tenant_id` foreign key |
| Soft deletes | Used for audit trails |
| JSONB | Used for flexible product attributes |

## PostgreSQL 16

- JSONB support for flexible schemas
- Used for product custom attributes
