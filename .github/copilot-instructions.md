# GitHub Copilot – Instructions (Stockshift Inventory API)

> **Goal:** Guide GitHub Copilot (and Copilot Chat) to generate code and artifacts aligned with this repository’s architecture, stack, and domain rules.

---

## 0) Project fingerprint (from Spring Initializr)

* **Build:** Gradle (Groovy DSL)
* **Java:** 17
* **Spring Boot:** 3.5.6
* **Group:** `com.stockshift`
* **Artifact/Name:** `backend`
* **Base package:** `com.stockshift.backend`
* **Dependencies:** Spring Web, Spring Data JPA, Spring Boot DevTools, Lombok, PostgreSQL Driver, Spring Security

> Copilot: always place new classes under `com.stockshift.backend` and follow the layer/package layout below.

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

**Key invariants (Copilot must preserve)**

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

## 4) REST API (v1) – resources & behavior

**Base path:** `/api/v1`

**Catalog**

* `GET/POST /products` – list/create a product
* `GET/PUT/DELETE /products/{id}` – delete = **inactivate**
* `GET/POST /products/{id}/variants` – manage SKUs
* `GET/POST /attributes/definitions` and `/attributes/definitions/{id}/values`
* `GET/POST /brands`, `GET/POST /categories`

**Warehouse & stock**

* `GET/POST /warehouses`
* `GET /warehouses/{id}/stock` – paginated stock projection for SKUs
* `POST /stock-events` – INBOUND / OUTBOUND / ADJUST (creates header + lines)
* `POST /stock-transfers` – create a draft transfer (from/to + lines)
* `POST /stock-transfers/{id}/confirm` – transactional confirmation; idempotent via `Idempotency-Key` header

**Users & roles**

* `POST /auth/login` – JWT
* `GET/POST /users` – ADMIN only
* `GET /roles` – list roles

**Conventions**

* Pagination: `page`, `size`; sorting: `sort=field,asc|desc`
* Errors: RFC 7807 (`application/problem+json`) via a global exception handler
* Validation: Jakarta Validation in DTOs; **re-check invariants in the domain**

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
* **Domain unit tests** covering: non‑negative balances, unconfirmed transfer, expiry, duplicate SKU, invalid category tree
* Static analysis optional: SpotBugs/Checkstyle; OWASP dependency check in CI

---

## 7) Gradle (Groovy) – guidance for Copilot

Use the Spring Boot plugin and Spring BOM. When asked to add libs, prefer:

* **Flyway:** `org.flywaydb:flyway-core`
* **OpenAPI:** `org.springdoc:springdoc-openapi-starter-webmvc-ui`
* **Testcontainers:** `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`

Common tasks: `./gradlew bootRun`, `test`, `build`

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

## 10) What Copilot **should generate**

* Thin controllers, DTOs, and mappers consistent with the endpoints above
* Application services with transactions enforcing stock invariants
* Domain entities/VOs with methods that keep aggregates consistent (e.g., `reserve`, `consume`, `confirmTransfer`)
* JPA repositories in `infrastructure` (interfaces in domain if needed)
* Flyway migrations for all schema changes (UUID keys, indexes, FKs)
* Tests (unit + integration) for critical use cases
* OpenAPI (springdoc) with schemas and examples when the dependency is added

## 11) What Copilot **must avoid**

* Placing business logic in controllers or repositories
* Updating `stock_items` directly without going through an event/use case
* Exposing JPA entities in responses
* Ignoring authorization by role/warehouse
* Creating catch‑all endpoints like `/executeOperation`

---

## 12) Useful prompt templates (Copilot Chat)

* "Generate DTOs and controllers for Products and ProductVariants under `com.stockshift.backend.api`, with Jakarta Validation and pagination. Do not expose JPA entities."
* "Implement the **application service** to confirm a stock transfer ensuring transactionality, balance ≥ 0, and idempotency via `Idempotency-Key` header."
* "Write initial Flyway migrations for Brand, Category (materialized path), AttributeDefinition/Value, Product, ProductVariant, Warehouse, StockItem, StockEvent/Line, StockTransfer. Include indexes and a UNIQUE constraint for SKU."
* "Create domain unit tests covering: outbound with insufficient stock, movement of expired items, double confirmation of a transfer, duplicate SKU, and invalid category cycles."
* "Add springdoc-openapi and expose Swagger UI only on the `dev` profile."

---

## 13) Incremental roadmap (generation order)

1. **Catalog:** Brand, Category (tree), Attributes
2. **Products & SKUs:** Product, ProductVariant (unique SKU)
3. **Warehouses & stock projection:** Warehouse, StockItem (read‑only projection)
4. **Events & Transfers:** INBOUND/OUTBOUND/ADJUST, transfer confirmation (transactional)
5. **Security:** JWT + roles + (optional) warehouse scoping
6. **Reports:** per‑warehouse balance, movement history, items near expiry

> Copilot: keep consistency with this document and any ADRs in `/docs/adr`.
