# Testing

## Commands

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "ClassName"

# Run tests matching pattern
./gradlew test --tests "*Controller*"
```

## Integration Tests

- Use **Testcontainers** with PostgreSQL
- Base class: `BaseIntegrationTest` for common setup
- Location: `src/test/java/br/com/stockshift/`

## Test Structure

```java
class SomeControllerTest extends BaseIntegrationTest {
    // BaseIntegrationTest provides:
    // - Test database via Testcontainers
    // - Common test utilities
    // - Authentication helpers
}
```
