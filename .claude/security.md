# Security

## Authentication

- JWT tokens stored in HTTP-only cookies
- Access token: 15 minutes TTL
- Refresh token: 7 days TTL
- Token denylist in Redis for logout

## Authorization

Role-based access control with granular permissions.

### Permission Format

```
{Resource}_{Action}_{Scope}
```

| Component | Values |
|-----------|--------|
| Actions | `CREATE`, `READ`, `UPDATE`, `DELETE` |
| Scopes | `OWN`, `ALL` |

### Examples

- `PRODUCT_CREATE_ALL` - Can create products for entire tenant
- `ORDER_READ_OWN` - Can only read own orders

## Rate Limiting

| Endpoint | Limit | Window |
|----------|-------|--------|
| Login | 10 requests | 15 minutes per IP |

Implemented with Bucket4j + Redis.
