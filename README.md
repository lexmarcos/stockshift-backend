# Stockshift Study Project

This repository is a learning experiment: we want to see how far a full **backend + frontend** stack can go when built entirely with AI assistance. Expect rough edges; the goal is to stress workflows, find gaps, and iterate.
To boost confidence while iterating with agents, every route is backed by unit tests and end-to-end coverage where feasible.

## Tech Snapshot

- **Java:** 17
- **Spring Boot:** 3.3.5
- **Gradle (Groovy DSL)**

## Why the Extra Docs?

To help future agents (and humans) produce consistent changes, we keep two concise guides in the repo:

- `AGENTS.md` — architecture & process instructions (layers, invariants, testing rules).
- `front-instructions/` — endpoint cheat sheets for frontend consumers (one Markdown per route group).

Read both before automating updates; they drastically reduce guesswork.

## Running the Backend

Assuming Java 17 is installed and PostgreSQL is reachable (defaults: `jdbc:postgresql://localhost:5432/stockshift`, user/password `stockshift`):

```bash
# Run with the Gradle wrapper (recommended)
./gradlew bootRun --no-daemon

# Or package + run the jar
./gradlew build --no-daemon
java -jar build/libs/backend-0.0.1-SNAPSHOT.jar
```

The API listens on `http://localhost:8080` by default.

## Test & Docs Routines

- **Unit tests** — `./gradlew test --no-daemon`
  - Focus on services and controllers; kept comprehensive so agents instantly spot regressions.
- **E2E API suites (JUnit)** — `./gradlew test --no-daemon --tests 'com.stockshift.backend.api.*'`
  - Live under `src/test/java/com/stockshift/backend/api/`; they exercise the HTTP surface end-to-end using `TestRestTemplate`.
- **Swagger UI** — `http://localhost:8080/swagger-ui/index.html`
  - Run the app, then explore the interactive docs (remember to add `Authorization: Bearer <token>` after logging in).

Creating and maintaining these checks was intentional: they act as guardrails so automated contributors can detect breaking changes quickly.
