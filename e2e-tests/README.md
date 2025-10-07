# Playwright API E2E Tests

Automated API checks built with [Playwright Test](https://playwright.dev/docs/test-api-testing). These suites exercise the backend over HTTP, mimicking real client workflows. They currently cover the **Users**, **Brands**, **Categories**, **Attributes**, **Products**, **Product Variants**, and **Warehouses** routes and will expand as we wire more features.

## Prerequisites

- Node.js 18+
- Backend running locally (`http://localhost:8080` by default)
- Admin credentials available (defaults to `testuser` / `testpass123`). Override via env vars:
  - `E2E_ADMIN_USERNAME`
  - `E2E_ADMIN_PASSWORD`
- Optional: set `API_BASE_URL` if the API is not on `http://localhost:8080`.

## Install & Run

```bash
cd e2e-tests
npm install

# Run all suites
npx playwright test

# Target only API suites explicitly
npx playwright test --project=api

# Filter by file or title
npx playwright test tests/users/users-create.spec.ts
```

Playwright stores reports under `playwright-report/` (generated when tests fail). To debug, run with verbose logging:

```bash
npx playwright test --trace on
```

## File Layout

```
e2e-tests/
├── playwright.config.ts      # Base URL, timeout, reporters
├── package.json              # Scripts & dependencies
├── tsconfig.json             # TypeScript config for Playwright
└── tests/
    ├── helpers/              # Auth & payload utilities
    ├── attributes/           # Attribute definition/value suites
    ├── categories/           # Categories API suites (create, read, hierarchy, lifecycle)
    ├── brands/               # Brands API suites (create, read, lifecycle)
    ├── products/             # Products API suites (create, read, lifecycle, expiry)
    ├── variants/             # Product variant suites (create, read, lifecycle)
    ├── warehouses/           # Warehouse suites (create, read, lifecycle)
    └── users/                # Users API suites (create, read, lifecycle)
```

## Adding New Suites

1. Create helper utilities (auth, payload factories) under `tests/helpers` if needed.
2. Add spec files inside `tests/<domain>/`.
3. Keep tests independent—each spec should set up and tear down its own data to stay idempotent.
4. Update this README when new coverage areas are added.

Happy testing!
