# AI Coding Agent – Instructions (Stockshift Inventory API)

> **Goal:** Guide any code-generation agent (CLI, editor, or chat based) to create artifacts aligned with this repository’s architecture, stack, and domain rules.

---

## 0) Project fingerprint (from Spring Initializr)

* **Build:** Gradle (Groovy DSL)
* **Java:** 17
* **Spring Boot:** 3.3.5
* **Group:** `com.stockshift`
* **Artifact/Name:** `backend`
* **Base package:** `com.stockshift.backend`
* **Dependencies:** Spring Web, Spring Data JPA, Spring Boot DevTools, Lombok, PostgreSQL Driver, Spring Security

> Agents: always place new classes under `com.stockshift.backend` and follow the layer/package layout below.

---

## 1) Architecture & layering (DDD‑lite / Hexagonal)

```
api (REST)          → controllers, DTOs, request/response mappers
application         → use cases, transactions, authorization checks
domain              → entities, value objects, domain services, invariants
infrastructure      → JPA repositories, configuration, adapters (db, external)
```

**Rules**

1. Controllers are thin (no business rules). They call **application services** only.
2. Domain is plain Java (avoid Spring annotations where possible). Keep invariants there.
3. Repositories expose domain aggregates; JPA specifics stay in `infrastructure`.
4. Method‑level security is applied in the **application** layer.

Folder suggestion:

```
src/main/java/com/stockshift/backend/
  api/
  application/
  domain/
  infrastructure/
src/main/resources/db/migration/   # Flyway (to be added)
```

---

## 2) Domain model (inventory)

* **Brand**
* **Category** (category tree; prefer *materialized path* or `parent_id + path`)
* **Product** (name, description, brand, category, `basePrice`, optional `expiryDate`)
* **AttributeDefinition** (e.g., Color, Size, Hair type)
* **AttributeValue** (e.g., Blue, S, Oily)
* **ProductVariant** (SKU): unique combination of `AttributeValue`s for a Product; has `sku` (unique) and optional `gtin`
* **Warehouse** (store/depot)
* **StockItem** (current quantity of a `variant` in a `warehouse`) – **projection**
* **StockEvent** (header) & **StockEventLine**: types = INBOUND, OUTBOUND, TRANSFER, ADJUST; **source of truth** for stock movement
* **StockTransfer** (header/lines) → **on confirmation** it generates OUTBOUND from origin and INBOUND to destination
* **User / Role** (ADMIN, MANAGER, SELLER) with optional scoping per warehouse

**Key invariants (agents must preserve)**

* **SKU is unique** (table `product_variants`) and immutable after creation.
* **Stock quantity must never be negative**; deny the use case if not enough quantity.
* **Transfers change stock only when confirmed**; confirmation is atomic.
* If `expiryDate` exists, movements with **expired** items are blocked (except explicit discard/adjust).
* **Category tree has no cycles**.
* Timestamps in **UTC**; API uses **ISO‑8601**.
* Concurrency: use **optimistic locking** (`@Version`) on sensitive aggregates.

---

## 3) Database & migrations (PostgreSQL)

* IDs: **UUID** (v7 or v4)
* Essential indexes:

  * `product_variants.sku` **UNIQUE**
  * (`stock_items.warehouse_id`, `stock_items.variant_id`) **UNIQUE**
  * Indexes for lookups by `brand_id`, `category path`, `gtin`
* Use **Flyway** for schema versioning (add dependency). Migration naming: `VYYYYMMDDHHmm__description.sql`

**`application.yml` (dev) – guidance**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/stockshift
    username: stockshift
    password: stockshift
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.time_zone: UTC
      hibernate.format_sql: true
  flyway:
    enabled: true
server:
  error.include-message: always
  error.include-binding-errors: always
```

---

## 4) REST API (v1) – current surface

**Base path:** `/api/v1`

**Authentication**

* `POST /auth/login` – issue access/refresh token pair.
* `POST /auth/refresh` – rotate tokens.
* `POST /auth/logout` – revoke refresh token when provided (idempotent).

**Brands** (`/brands`)

* `POST /` – create (ADMIN, MANAGER).
* `GET /` – list (toggle `onlyActive`); pageable (`page`, `size`, `sort`).
* `GET /{id}` – fetch by id.
* `GET /name/{name}` – fetch by name.
* `PUT /{id}` – update (ADMIN, MANAGER).
* `DELETE /{id}` – soft delete → sets `active=false` (ADMIN).
* `PATCH /{id}/activate` – reactivate (ADMIN).

**Categories** (`/categories`)

* `POST /` – create (ADMIN, MANAGER).
* `GET /` – list (toggle `onlyActive`); pageable.
* `GET /root` – list root categories.
* `GET /{id}` – fetch by id.
* `GET /name/{name}` – fetch by name.
* `GET /{id}/subcategories` – paginated children.
* `GET /{id}/descendants` – list full subtree.
* `PUT /{id}` – update (ADMIN, MANAGER).
* `DELETE /{id}` – soft delete (ADMIN).
* `PATCH /{id}/activate` – reactivate (ADMIN).

**Attributes** (`/attributes`)

* Definitions
  * `POST /definitions` – create (ADMIN, MANAGER).
  * `GET /definitions` – list (toggle `onlyActive`).
  * `GET /definitions/{id}` – fetch by id.
  * `GET /definitions/code/{code}` – fetch by code.
  * `PUT /definitions/{id}` – update (ADMIN, MANAGER).
  * `DELETE /definitions/{id}` – soft delete (ADMIN).
  * `PATCH /definitions/{id}/activate` – reactivate (ADMIN).
* Values
  * `POST /definitions/{definitionId}/values` – create (ADMIN, MANAGER).
  * `GET /definitions/{definitionId}/values` – list (toggle `onlyActive`).
  * `GET /values/{id}` – fetch by id.
  * `PUT /values/{id}` – update (ADMIN, MANAGER).
  * `DELETE /values/{id}` – soft delete (ADMIN).
  * `PATCH /values/{id}/activate` – reactivate (ADMIN).

**Products** (`/products`)

* `POST /` – create (ADMIN, MANAGER); optional brand/category linking.
* `GET /` – list; switch to active-only via `onlyActive`.
* `GET /search` – search by name (`q`).
* `GET /brand/{brandId}` – paginated by brand.
* `GET /category/{categoryId}` – paginated by category.
* `GET /expired` – expired products.
* `GET /expiring-soon` – expiring window (`days` default 30).
* `GET /{id}` – fetch by id.
* `GET /name/{name}` – fetch active product by name.
* `PUT /{id}` – update (ADMIN, MANAGER) with conflict checks.
* `DELETE /{id}` – soft delete (ADMIN).
* `PATCH /{id}/activate` – reactivate (ADMIN).

**Product variants** (`/products/{id}/variants`, `/variants`)

* `POST /products/{productId}/variants` – create (ADMIN, MANAGER).
* `GET /products/{productId}/variants` – paginated list for a product (optionally filter active).
* `GET /variants` – paginated global listing (`onlyActive`).
* `GET /variants/{id}` – fetch by id.
* `GET /variants/sku/{sku}` – fetch by SKU (throws if missing).
* `GET /variants/gtin/{gtin}` – fetch by GTIN (throws if missing).
* `PUT /variants/{id}` – update (ADMIN, MANAGER).
* `DELETE /variants/{id}` – soft delete (ADMIN).
* `PATCH /variants/{id}/activate` – reactivate (ADMIN).

**Warehouses** (`/warehouses`)

* `POST /` – create (ADMIN, MANAGER).
* `GET /` – list (toggle `onlyActive`).
* `GET /type/{type}` – filter by `WarehouseType` (respects `onlyActive`).
* `GET /search` – search by name/code (`query`).
* `GET /{id}` – fetch by id.
* `GET /code/{code}` – fetch by code.
* `PUT /{id}` – update (ADMIN, MANAGER).
* `DELETE /{id}` – soft delete (ADMIN).
* `PATCH /{id}/activate` – reactivate (ADMIN).

**Users** (`/users`)

* `POST /` – create (ADMIN).
* `GET /` – paginated list (ADMIN, MANAGER).
* `GET /{id}` – fetch by id (ADMIN, MANAGER).
* `GET /username/{username}` – fetch by username (ADMIN, MANAGER).
* `PUT /{id}` – update (ADMIN).
* `DELETE /{id}` – soft delete (ADMIN).
* `PATCH /{id}/activate` – reactivate (ADMIN).

**Developer & diagnostics**

* `GET /dev/test-user` – returns seeded credentials when `dev` profile active.
* `GET /test/public` – unauthenticated ping.
* `GET /test/authenticated` – echo authenticated principal.
* `GET /test/admin` – requires `ADMIN`.
* `GET /test/manager` – requires `ADMIN` or `MANAGER`.

**Cross-cutting conventions**

* Prefer pagination via Spring's `Pageable` parameters: `page`, `size`, `sort` (e.g., `sort=name,asc`). Default page size is 20 unless stated otherwise.
* Soft deletes flip the `active` flag; dedicated `PATCH .../activate` restores records. Downstream logic must respect `active` status.
* Responses return DTOs via mappers; controllers never expose entities directly.
* Validation uses Jakarta annotations on request DTOs; enforce invariants again inside services/domain objects.

---

## 5) Security

* **Spring Security + JWT (stateless)**
* Roles: `ADMIN`, `MANAGER`, `SELLER`
* SELLER: read + OUTBOUND only in **authorized warehouses**
* Prefer **method security** in the application layer (`@PreAuthorize`)
* CORS: strict per environment; never `*` in production

---

## 6) Testing & quality

* JUnit 5 + AssertJ
* **Testcontainers (PostgreSQL)** for integration tests
* `@DataJpaTest` for repositories (no web context)
* **Domain unit tests** covering: non-negative balances, unconfirmed transfer, expiry, duplicate SKU, invalid category tree
* Static analysis optional: SpotBugs/Checkstyle; OWASP dependency check in CI

**E2E test playbook (SpringBootTest + TestRestTemplate)**

* Target the dev profile or default environment (assumes development) when running E2E suites; the seeded test user only exists here. Enable via `SPRING_PROFILES_ACTIVE=dev` or `--spring.profiles.active=dev`.
* Ensure `app.test-user.enabled=true` (see `application-dev.properties`) so the bootstrapper creates the fixed-credential user. Tests may invoke `/api/v1/dev/test-user` to fetch credentials when needed.
* Default credentials:
  * username `testuser`, password `testpass123`
  * access token `dev-access-token-...`, refresh token `dev-refresh-token-...` (valid for dev only)
* Prefer authenticating once per suite using `/api/v1/auth/login` with the test user and cache the bearer token. For smokier checks, the fixed access token is acceptable.
* Use `TestRestTemplate` (already configured in current suites) with JSON headers + bearer token helper methods. Keep lifecycle tests idempotent by generating unique suffixes (`UUID.randomUUID()` etc.).
* Guardrail: never assume these credentials exist in production or CI prod-like runs; wrap usage in dev-only toggles where appropriate.

**Critical: Controller `@PathVariable` and `@RequestParam` annotations**

* **Always specify the parameter name explicitly** in `@PathVariable` and `@RequestParam` annotations in controllers.
* ✅ **Correct:** `@PathVariable("id") UUID id` or `@RequestParam("name") String name`
* ❌ **Wrong:** `@PathVariable UUID id` or `@RequestParam String name`
* **Reason:** VS Code uses the Eclipse JDT compiler, which does not preserve parameter names by default even with Gradle's `-parameters` flag. Omitting the explicit name causes a runtime error: `"Name for argument of type [java.util.UUID] not specified, and parameter name information not available via reflection"`.
* **Impact:** Tests pass in Gradle CLI but fail when run from VS Code's test runner with `400 BAD_REQUEST`.
* **Rule:** When creating or modifying controllers, always include explicit names in Spring MVC annotations to ensure compatibility across all compilation environments.

> **Agents: pay special attention to the following rules whenever modifying or generating code in the `application/service` layer:**

1. **Whenever you modify an existing service** (e.g., adding, renaming, or changing business logic in `src/main/java/com/stockshift/backend/application/service`), you must **immediately run all unit tests** under:

   ```
   src/test/java/com/stockshift/backend/application/service
   ```

   This ensures that no existing tests break after your change.

2. **Whenever you create or update any service**, you must **create or update corresponding unit tests** under the same mirrored package in `src/test/java/...`.
   Each public method in the service should have at least one test verifying expected behavior and invariants.

3. Use **Mockito** for dependencies, avoiding database or network operations.
   Services should be tested in isolation, validating input/output and interactions with repositories or domain entities.

4. All new or updated tests must be **runnable via**:

   ```bash
   ./gradlew test
   ```

   and must **pass successfully** before considering the change complete.

5. When a test fails, analyze the cause (regression or new logic).
   If it’s due to new expected behavior, update the corresponding test case instead of deleting or commenting it out.

---

## 7) Gradle (Groovy) – guidance for agents

Use the Spring Boot plugin and Spring BOM. When asked to add libs, prefer:

* **Flyway:** `org.flywaydb:flyway-core`
* **OpenAPI:** `org.springdoc:springdoc-openapi-starter-webmvc-ui`
* **Testcontainers:** `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`

Common tasks: `./gradlew bootRun`, `test`, `build`

**Gradle execution rules:**

* **Always use `--no-daemon` flag** when running Gradle commands in terminal tools.
* ✅ **Correct:** `./gradlew test --no-daemon` or `./gradlew build --no-daemon`
* ❌ **Wrong:** `./gradlew test` (without `--no-daemon`)
* **Reason:** The Gradle daemon can cause resource leaks and conflicts in development environments, especially when running tests or builds from automated agents.
* **Rule:** All Gradle commands executed via terminal tools must include the `--no-daemon` flag to ensure clean, isolated builds.

---

## 8) Coding conventions

* **Language:** English for code **and** docs
* **Lombok:** allowed (already included). Prefer Lombok for simple DTOs; keep domain with explicit constructors/methods when invariants matter
* DTOs ≠ JPA entities. **Never** expose entities in API responses
* Use `OffsetDateTime`/`Instant` (UTC) for time
* Dedicated mappers (MapStruct recommended when added)

---

## 9) Observability & ops

* Spring Boot **Actuator** enabled (health, info). Expose metrics only in secure environments
* Structured logs (JSON) in production; mask sensitive data

---

## 10) What agents **should generate**

* Thin controllers, DTOs, and mappers consistent with the endpoints above
* Application services with transactions enforcing stock invariants
* Domain entities/VOs with methods that keep aggregates consistent (e.g., `reserve`, `consume`, `confirmTransfer`)
* JPA repositories in `infrastructure` (interfaces in domain if needed)
* Flyway migrations for all schema changes (UUID keys, indexes, FKs)
* Tests (unit + integration) for critical use cases
* OpenAPI (springdoc) with schemas and examples when the dependency is added

## 11) What agents **must avoid**

* Placing business logic in controllers or repositories
* Updating `stock_items` directly without going through an event/use case
* Exposing JPA entities in responses
* Ignoring authorization by role/warehouse
* Creating catch‑all endpoints like `/executeOperation`

---

## 12) Useful prompt templates (chat-based assistants)

* "Generate DTOs and controllers for Products and ProductVariants under `com.stockshift.backend.api`, with Jakarta Validation and pagination. Do not expose JPA entities."
* "Implement the **application service** to confirm a stock transfer ensuring transactionality, balance ≥ 0, and idempotency via `Idempotency-Key` header."
* "Write initial Flyway migrations for Brand, Category (materialized path), AttributeDefinition/Value, Product, ProductVariant, Warehouse, StockItem, StockEvent/Line, StockTransfer. Include indexes and a UNIQUE constraint for SKU."
* "Create domain unit tests covering: outbound with insufficient stock, movement of expired items, double confirmation of a transfer, duplicate SKU, and invalid category cycles."
* "Add springdoc-openapi and expose Swagger UI only on the `dev` profile."

---

## 14) Workflow & communication rules

* Do **not** create standalone Markdown documents (e.g., README updates, CHANGELOG entries, or new *.md files) unless the user **explicitly requests** their creation. For regular tasks, provide a short summary of what you generated/modified **in the agent response only**.

* After **any modification or addition** within `application/service`, **run all unit tests** in `src/test/java/com/stockshift/backend/application/service` and **ensure coverage for the modified logic** before committing changes.
