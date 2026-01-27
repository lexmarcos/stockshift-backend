# Configuration

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active profile | `dev` |
| `JWT_SECRET` | JWT signing key | dev key |
| `JWT_ACCESS_EXPIRATION` | Access token TTL (ms) | 900000 |
| `JWT_REFRESH_EXPIRATION` | Refresh token TTL (ms) | 604800000 |
| `REDIS_HOST` | Redis hostname | localhost |
| `REDIS_PORT` | Redis port | 6379 |
| `ALLOWED_ORIGINS` | CORS origins | localhost:3000,9000 |

## Local Development

1. Start Docker services:
   ```bash
   docker-compose up -d
   ```

2. Run application:
   ```bash
   ./gradlew bootRun
   ```

3. Access API docs:
   ```
   http://localhost:8080/stockshift/swagger-ui.html
   ```

## Docker Services

- **PostgreSQL 16** - Main database
- **Redis** - Token denylist, rate limiting
- **pgAdmin** - Database administration UI
