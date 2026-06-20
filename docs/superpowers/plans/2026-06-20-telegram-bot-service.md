# Telegram Bot Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a separate TypeScript Telegram bot service that accepts text/audio product questions, uses OpenAI for transcription and intent extraction, stores warehouse preferences in SQLite, and calls the StockShift internal bot API.

**Architecture:** Create a top-level `stockshift-telegram-bot` Node service using `grammY`, an HTTP webhook server, SQLite for per-user warehouse defaults, project-owned clients for OpenAI and StockShift, and Docker Compose deployment on the VPS. The bot never reads the StockShift database directly and never persists questions, transcriptions, audio, or answers.

**Tech Stack:** Node 22, TypeScript 5, pnpm 10.30.1, grammY, OpenAI SDK, zod, better-sqlite3, Vitest, Docker, Docker Compose, Caddy.

## Global Constraints

- Bot service path is `/home/suel/projects/stockshift/stockshift-telegram-bot`.
- Bot uses Telegram webhook, not polling.
- Webhook URL is `https://$STOCKSHIFT_DOMAIN/stockshift/telegram-bot/webhook`.
- Access is restricted by `TELEGRAM_ALLOWED_USER_IDS`.
- Audio is downloaded, transcribed, and discarded without persistence.
- Text, questions, transcriptions, and answers are not persisted.
- Warehouse preferences persist in SQLite only.
- OpenAI transcription model defaults to `gpt-4o-mini-transcribe`.
- OpenAI intent model defaults to `gpt-4.1-nano`.
- Intent confidence below `0.60` asks for clarification.
- Product result limit defaults to `5`.
- StockShift calls use `X-StockShift-Bot-Key`.
- The backend internal API plan must be implemented before live end-to-end bot verification.
- TypeScript uses strict mode and explicit types; avoid `any`.

---

## File Structure

- Create `stockshift-telegram-bot/package.json`: scripts, runtime deps, dev deps, package manager.
- Create `stockshift-telegram-bot/tsconfig.json`: strict NodeNext TypeScript config.
- Create `stockshift-telegram-bot/eslint.config.mjs`: flat ESLint config for TypeScript.
- Create `stockshift-telegram-bot/vitest.config.ts`: Vitest config.
- Create `stockshift-telegram-bot/.gitignore`, `.dockerignore`, `Dockerfile`, `.env.example`, `README.md`.
- Create `stockshift-telegram-bot/src/config/env.ts`: validates and parses env.
- Create `stockshift-telegram-bot/src/server/http-server.ts`: health route and webhook routing.
- Create `stockshift-telegram-bot/src/storage/sqlite-connection.ts`: opens SQLite and migrates schema.
- Create `stockshift-telegram-bot/src/storage/warehouse-preferences-repository.ts`: reads/writes default warehouse preferences.
- Create `stockshift-telegram-bot/src/types/stockshift.ts`: StockShift API DTOs.
- Create `stockshift-telegram-bot/src/clients/stockshift-bot-api-client.ts`: backend API client.
- Create `stockshift-telegram-bot/src/services/product-response-formatter.ts`: Telegram caption/text formatting.
- Create `stockshift-telegram-bot/src/types/intent.ts`: OpenAI intent DTO.
- Create `stockshift-telegram-bot/src/clients/openai-bot-client.ts`: OpenAI transcription and JSON intent extraction.
- Create `stockshift-telegram-bot/src/services/warehouse-resolution-service.ts`: chooses mentioned warehouse or stored default.
- Create `stockshift-telegram-bot/src/services/product-query-service.ts`: orchestrates intent, warehouse, StockShift query, and formatting.
- Create `stockshift-telegram-bot/src/bot/telegram-audio-downloader.ts`: downloads Telegram voice/audio files into memory.
- Create `stockshift-telegram-bot/src/bot/telegram-bot.ts`: registers commands, access guard, callbacks, and message handlers.
- Create `stockshift-telegram-bot/src/main.ts`: production composition and webhook setup.
- Modify `stockshift-infra/docker-compose.prod.yml`: add bot container, volume, healthcheck, network aliases.
- Modify `stockshift-infra/.env.production.example`: add bot env vars.
- Modify `stockshift-infra/README.md`: document bot deployment variables.
- Modify `vps-infra/Caddyfile`: route webhook path to bot container.
- Modify `vps-infra/README.md`: document bot alias.

---

### Task 1: Node Service Scaffold And Health Server

**Files:**
- Create: `stockshift-telegram-bot/package.json`
- Create: `stockshift-telegram-bot/tsconfig.json`
- Create: `stockshift-telegram-bot/eslint.config.mjs`
- Create: `stockshift-telegram-bot/vitest.config.ts`
- Create: `stockshift-telegram-bot/.gitignore`
- Create: `stockshift-telegram-bot/.dockerignore`
- Create: `stockshift-telegram-bot/Dockerfile`
- Create: `stockshift-telegram-bot/.env.example`
- Create: `stockshift-telegram-bot/src/config/env.ts`
- Create: `stockshift-telegram-bot/src/server/http-server.ts`
- Create: `stockshift-telegram-bot/src/main.ts`
- Test: `stockshift-telegram-bot/src/config/env.test.ts`
- Test: `stockshift-telegram-bot/src/server/http-server.test.ts`

**Interfaces:**
- Consumes: process environment.
- Produces: `BotEnv`, `loadBotEnv(source)`, `createHttpServer(options)`, `GET /health`, and a webhook route based on `BOT_PUBLIC_WEBHOOK_URL`.

- [ ] **Step 1: Create package and tooling files**

Create the service directory:

```bash
mkdir -p /home/suel/projects/stockshift/stockshift-telegram-bot
```

Create `package.json`:

```json
{
  "name": "stockshift-telegram-bot",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "packageManager": "pnpm@10.30.1",
  "scripts": {
    "dev": "tsx watch src/main.ts",
    "build": "tsc",
    "start": "node dist/main.js",
    "lint": "eslint .",
    "typecheck": "tsc --noEmit",
    "test": "vitest run"
  },
  "dependencies": {
    "better-sqlite3": "^12.4.6",
    "grammy": "^1.38.4",
    "openai": "^6.10.0",
    "zod": "^4.1.12"
  },
  "devDependencies": {
    "@eslint/js": "^9.39.1",
    "@types/better-sqlite3": "^7.6.13",
    "@types/node": "^24.10.1",
    "eslint": "^9.39.1",
    "tsx": "^4.21.0",
    "typescript": "^5.9.3",
    "typescript-eslint": "^8.46.4",
    "vitest": "^4.0.14"
  }
}
```

Create `tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "lib": ["ES2022"],
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "exactOptionalPropertyTypes": true,
    "esModuleInterop": true,
    "forceConsistentCasingInFileNames": true,
    "skipLibCheck": true,
    "rootDir": "src",
    "outDir": "dist"
  },
  "include": ["src/**/*.ts"]
}
```

Create `eslint.config.mjs`:

```js
import js from "@eslint/js";
import tseslint from "typescript-eslint";

export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.recommendedTypeChecked,
  {
    languageOptions: {
      parserOptions: {
        project: "./tsconfig.json",
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },
  {
    ignores: ["dist/**", "coverage/**", "node_modules/**"],
  }
);
```

Create `vitest.config.ts`:

```ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
});
```

Create `.gitignore`:

```gitignore
node_modules/
dist/
coverage/
.env
.env.*
!.env.example
*.sqlite
*.sqlite-shm
*.sqlite-wal
```

Create `.dockerignore`:

```dockerignore
node_modules
dist
coverage
.env
*.sqlite
*.sqlite-shm
*.sqlite-wal
```

Create `.env.example`:

```dotenv
PORT=3001
TELEGRAM_BOT_TOKEN=change-me
TELEGRAM_ALLOWED_USER_IDS=123456789,987654321
OPENAI_API_KEY=change-me
OPENAI_TRANSCRIPTION_MODEL=gpt-4o-mini-transcribe
OPENAI_INTENT_MODEL=gpt-4.1-nano
STOCKSHIFT_API_BASE_URL=https://stockshift.example.com/stockshift
STOCKSHIFT_BOT_API_KEY=change-me
BOT_PUBLIC_WEBHOOK_URL=https://stockshift.example.com/stockshift/telegram-bot/webhook
SQLITE_PATH=/data/telegram-bot.sqlite
BOT_MAX_PRODUCT_RESULTS=5
```

Create `Dockerfile`:

```dockerfile
FROM node:22-alpine AS deps
WORKDIR /app
RUN corepack enable && apk add --no-cache python3 make g++
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile=false

FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN pnpm build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
RUN corepack enable && apk add --no-cache python3 make g++
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install --prod --frozen-lockfile=false
COPY --from=build /app/dist ./dist
EXPOSE 3001
CMD ["node", "dist/main.js"]
```

- [ ] **Step 2: Write failing env tests**

Create `src/config/env.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { loadBotEnv } from "./env.js";

describe("loadBotEnv", () => {
  it("parses required env and defaults", () => {
    const env = loadBotEnv({
      TELEGRAM_BOT_TOKEN: "telegram-token",
      TELEGRAM_ALLOWED_USER_IDS: "123, 456",
      OPENAI_API_KEY: "openai-key",
      STOCKSHIFT_API_BASE_URL: "https://stockshift.example.com/stockshift/",
      STOCKSHIFT_BOT_API_KEY: "bot-key",
      BOT_PUBLIC_WEBHOOK_URL: "https://stockshift.example.com/stockshift/telegram-bot/webhook",
      SQLITE_PATH: "/tmp/bot.sqlite",
    });

    expect(env.PORT).toBe(3001);
    expect(env.TELEGRAM_ALLOWED_USER_IDS).toEqual(new Set(["123", "456"]));
    expect(env.STOCKSHIFT_API_BASE_URL).toBe("https://stockshift.example.com/stockshift");
    expect(env.BOT_MAX_PRODUCT_RESULTS).toBe(5);
    expect(env.OPENAI_TRANSCRIPTION_MODEL).toBe("gpt-4o-mini-transcribe");
    expect(env.OPENAI_INTENT_MODEL).toBe("gpt-4.1-nano");
  });

  it("rejects an empty allowed user list", () => {
    expect(() => loadBotEnv({
      TELEGRAM_BOT_TOKEN: "telegram-token",
      TELEGRAM_ALLOWED_USER_IDS: "",
      OPENAI_API_KEY: "openai-key",
      STOCKSHIFT_API_BASE_URL: "https://stockshift.example.com/stockshift",
      STOCKSHIFT_BOT_API_KEY: "bot-key",
      BOT_PUBLIC_WEBHOOK_URL: "https://stockshift.example.com/stockshift/telegram-bot/webhook",
      SQLITE_PATH: "/tmp/bot.sqlite",
    })).toThrow();
  });
});
```

- [ ] **Step 3: Write failing HTTP server tests**

Create `src/server/http-server.test.ts`:

```ts
import { AddressInfo } from "node:net";
import { afterEach, describe, expect, it } from "vitest";
import { createHttpServer } from "./http-server.js";

describe("createHttpServer", () => {
  const servers: ReturnType<typeof createHttpServer>[] = [];

  afterEach(async () => {
    await Promise.all(servers.map((server) => new Promise<void>((resolve) => server.close(() => resolve()))));
  });

  it("responds to health checks", async () => {
    const server = createHttpServer({
      webhookPath: "/stockshift/telegram-bot/webhook",
      webhook: (_request, response) => {
        response.statusCode = 204;
        response.end();
      },
    });
    servers.push(server);
    await new Promise<void>((resolve) => server.listen(0, resolve));
    const address = server.address() as AddressInfo;

    const response = await fetch(`http://127.0.0.1:${address.port}/health`);

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ status: "ok" });
  });

  it("routes webhook posts to the webhook callback", async () => {
    let called = false;
    const server = createHttpServer({
      webhookPath: "/stockshift/telegram-bot/webhook",
      webhook: (_request, response) => {
        called = true;
        response.statusCode = 204;
        response.end();
      },
    });
    servers.push(server);
    await new Promise<void>((resolve) => server.listen(0, resolve));
    const address = server.address() as AddressInfo;

    const response = await fetch(`http://127.0.0.1:${address.port}/stockshift/telegram-bot/webhook`, {
      method: "POST",
    });

    expect(response.status).toBe(204);
    expect(called).toBe(true);
  });
});
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `pnpm install && pnpm test`

Expected: FAIL because `env.ts` and `http-server.ts` do not exist.

- [ ] **Step 5: Implement env parsing**

Create `src/config/env.ts`:

```ts
import { z } from "zod";

const rawEnvSchema = z.object({
  PORT: z.coerce.number().int().positive().default(3001),
  TELEGRAM_BOT_TOKEN: z.string().min(1),
  TELEGRAM_ALLOWED_USER_IDS: z.string().min(1),
  OPENAI_API_KEY: z.string().min(1),
  OPENAI_TRANSCRIPTION_MODEL: z.string().min(1).default("gpt-4o-mini-transcribe"),
  OPENAI_INTENT_MODEL: z.string().min(1).default("gpt-4.1-nano"),
  STOCKSHIFT_API_BASE_URL: z.string().url(),
  STOCKSHIFT_BOT_API_KEY: z.string().min(1),
  BOT_PUBLIC_WEBHOOK_URL: z.string().url(),
  SQLITE_PATH: z.string().min(1),
  BOT_MAX_PRODUCT_RESULTS: z.coerce.number().int().positive().max(10).default(5),
});

export interface BotEnv {
  PORT: number;
  TELEGRAM_BOT_TOKEN: string;
  TELEGRAM_ALLOWED_USER_IDS: Set<string>;
  OPENAI_API_KEY: string;
  OPENAI_TRANSCRIPTION_MODEL: string;
  OPENAI_INTENT_MODEL: string;
  STOCKSHIFT_API_BASE_URL: string;
  STOCKSHIFT_BOT_API_KEY: string;
  BOT_PUBLIC_WEBHOOK_URL: string;
  SQLITE_PATH: string;
  BOT_MAX_PRODUCT_RESULTS: number;
}

export function loadBotEnv(source: NodeJS.ProcessEnv): BotEnv {
  const rawEnv = rawEnvSchema.parse(source);
  const allowedUserIds = parseAllowedUserIds(rawEnv.TELEGRAM_ALLOWED_USER_IDS);
  return {
    ...rawEnv,
    TELEGRAM_ALLOWED_USER_IDS: allowedUserIds,
    STOCKSHIFT_API_BASE_URL: rawEnv.STOCKSHIFT_API_BASE_URL.replace(/\/$/, ""),
  };
}

function parseAllowedUserIds(value: string): Set<string> {
  const ids = value.split(",")
    .map((id) => id.trim())
    .filter((id) => id.length > 0);
  if (ids.length === 0) {
    throw new Error("TELEGRAM_ALLOWED_USER_IDS must contain at least one Telegram user id");
  }
  return new Set(ids);
}
```

- [ ] **Step 6: Implement HTTP server and initial main**

Create `src/server/http-server.ts`:

```ts
import { createServer, IncomingMessage, Server, ServerResponse } from "node:http";

export type WebhookCallback = (request: IncomingMessage, response: ServerResponse) => void | Promise<void>;

export interface HttpServerOptions {
  webhookPath: string;
  webhook: WebhookCallback;
}

export function createHttpServer(options: HttpServerOptions): Server {
  return createServer(async (request, response) => {
    const pathname = new URL(request.url ?? "/", "http://localhost").pathname;
    if (request.method === "GET" && pathname === "/health") {
      sendJson(response, 200, { status: "ok" });
      return;
    }
    if (request.method === "POST" && pathname === options.webhookPath) {
      await options.webhook(request, response);
      return;
    }
    sendJson(response, 404, { error: "not_found" });
  });
}

function sendJson(response: ServerResponse, statusCode: number, body: object): void {
  response.statusCode = statusCode;
  response.setHeader("content-type", "application/json");
  response.end(JSON.stringify(body));
}
```

Create `src/main.ts`:

```ts
import { loadBotEnv } from "./config/env.js";
import { createHttpServer } from "./server/http-server.js";

const env = loadBotEnv(process.env);
const webhookPath = new URL(env.BOT_PUBLIC_WEBHOOK_URL).pathname;

const server = createHttpServer({
  webhookPath,
  webhook: (_request, response) => {
    response.statusCode = 503;
    response.end("Telegram bot is not wired yet");
  },
});

server.listen(env.PORT, () => {
  console.log(JSON.stringify({ event: "telegram_bot_listening", port: env.PORT, webhookPath }));
});
```

- [ ] **Step 7: Run scaffold verification**

Run: `pnpm lint && pnpm typecheck && pnpm test && pnpm build`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
cd /home/suel/projects/stockshift/stockshift-telegram-bot
git init
git add .
git commit -m "feat: scaffold telegram bot service"
```

---

### Task 2: SQLite Warehouse Preferences

**Files:**
- Create: `stockshift-telegram-bot/src/storage/sqlite-connection.ts`
- Create: `stockshift-telegram-bot/src/storage/warehouse-preferences-repository.ts`
- Test: `stockshift-telegram-bot/src/storage/warehouse-preferences-repository.test.ts`

**Interfaces:**
- Consumes: `SQLITE_PATH` from `BotEnv`.
- Produces: `createSqliteDatabase(path)`, `migrateWarehousePreferences(db)`, `WarehousePreferencesRepository.getPreference(userId)`, and `WarehousePreferencesRepository.savePreference(preference)`.

- [ ] **Step 1: Write failing repository test**

Create `src/storage/warehouse-preferences-repository.test.ts`:

```ts
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { createSqliteDatabase, migrateWarehousePreferences } from "./sqlite-connection.js";
import { WarehousePreferencesRepository } from "./warehouse-preferences-repository.js";

describe("WarehousePreferencesRepository", () => {
  const directories: string[] = [];

  afterEach(() => {
    for (const directory of directories) {
      rmSync(directory, { recursive: true, force: true });
    }
  });

  it("saves and reads a warehouse preference", () => {
    const directory = mkdtempSync(join(tmpdir(), "stockshift-bot-"));
    directories.push(directory);
    const database = createSqliteDatabase(join(directory, "bot.sqlite"));
    migrateWarehousePreferences(database);
    const repository = new WarehousePreferencesRepository(database);

    repository.savePreference({
      telegramUserId: "123",
      warehouseId: "warehouse-1",
      warehouseName: "Centro",
    });

    expect(repository.getPreference("123")).toMatchObject({
      telegramUserId: "123",
      warehouseId: "warehouse-1",
      warehouseName: "Centro",
    });
  });

  it("returns null when no preference exists", () => {
    const directory = mkdtempSync(join(tmpdir(), "stockshift-bot-"));
    directories.push(directory);
    const database = createSqliteDatabase(join(directory, "bot.sqlite"));
    migrateWarehousePreferences(database);
    const repository = new WarehousePreferencesRepository(database);

    expect(repository.getPreference("missing")).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test src/storage/warehouse-preferences-repository.test.ts`

Expected: FAIL because storage files do not exist.

- [ ] **Step 3: Implement SQLite connection**

Create `src/storage/sqlite-connection.ts`:

```ts
import Database from "better-sqlite3";

export type SqliteDatabase = Database.Database;

export function createSqliteDatabase(path: string): SqliteDatabase {
  const database = new Database(path);
  database.pragma("journal_mode = WAL");
  return database;
}

export function migrateWarehousePreferences(database: SqliteDatabase): void {
  database.exec(`
    CREATE TABLE IF NOT EXISTS telegram_user_warehouse_preferences (
      telegram_user_id TEXT PRIMARY KEY,
      warehouse_id TEXT NOT NULL,
      warehouse_name TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );
  `);
}
```

- [ ] **Step 4: Implement preferences repository**

Create `src/storage/warehouse-preferences-repository.ts`:

```ts
import { SqliteDatabase } from "./sqlite-connection.js";

export interface WarehousePreference {
  telegramUserId: string;
  warehouseId: string;
  warehouseName: string;
  updatedAt: string;
}

export interface SaveWarehousePreferenceInput {
  telegramUserId: string;
  warehouseId: string;
  warehouseName: string;
}

interface WarehousePreferenceRow {
  telegram_user_id: string;
  warehouse_id: string;
  warehouse_name: string;
  updated_at: string;
}

export class WarehousePreferencesRepository {
  constructor(private readonly database: SqliteDatabase) {}

  getPreference(telegramUserId: string): WarehousePreference | null {
    const row = this.database.prepare(`
      SELECT telegram_user_id, warehouse_id, warehouse_name, updated_at
      FROM telegram_user_warehouse_preferences
      WHERE telegram_user_id = ?
    `).get(telegramUserId) as WarehousePreferenceRow | undefined;
    return row ? this.toPreference(row) : null;
  }

  savePreference(input: SaveWarehousePreferenceInput): void {
    this.database.prepare(`
      INSERT INTO telegram_user_warehouse_preferences (
        telegram_user_id, warehouse_id, warehouse_name, updated_at
      ) VALUES (?, ?, ?, ?)
      ON CONFLICT(telegram_user_id) DO UPDATE SET
        warehouse_id = excluded.warehouse_id,
        warehouse_name = excluded.warehouse_name,
        updated_at = excluded.updated_at
    `).run(input.telegramUserId, input.warehouseId, input.warehouseName, new Date().toISOString());
  }

  private toPreference(row: WarehousePreferenceRow): WarehousePreference {
    return {
      telegramUserId: row.telegram_user_id,
      warehouseId: row.warehouse_id,
      warehouseName: row.warehouse_name,
      updatedAt: row.updated_at,
    };
  }
}
```

- [ ] **Step 5: Run storage tests**

Run: `pnpm test src/storage/warehouse-preferences-repository.test.ts`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /home/suel/projects/stockshift/stockshift-telegram-bot
git add src/storage
git commit -m "feat: persist default warehouses in sqlite"
```

---

### Task 3: StockShift API Client And Product Formatting

**Files:**
- Create: `stockshift-telegram-bot/src/types/stockshift.ts`
- Create: `stockshift-telegram-bot/src/clients/stockshift-bot-api-client.ts`
- Create: `stockshift-telegram-bot/src/services/product-response-formatter.ts`
- Test: `stockshift-telegram-bot/src/clients/stockshift-bot-api-client.test.ts`
- Test: `stockshift-telegram-bot/src/services/product-response-formatter.test.ts`

**Interfaces:**
- Consumes: backend internal API from backend plan.
- Produces: `StockShiftBotApiClient.listWarehouses()`, `searchWarehouses(query)`, `searchProducts(input)`, and `formatProductCaption(product)`.

- [ ] **Step 1: Write failing client tests**

Create `src/clients/stockshift-bot-api-client.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { StockShiftBotApiClient } from "./stockshift-bot-api-client.js";

describe("StockShiftBotApiClient", () => {
  it("sends bot key and parses product search response", async () => {
    const requests: Request[] = [];
    const client = new StockShiftBotApiClient({
      baseUrl: "https://stockshift.example.com/stockshift",
      apiKey: "bot-key",
      fetcher: async (input, init) => {
        const request = new Request(input, init);
        requests.push(request);
        return Response.json({
          success: true,
          data: {
            hasMore: false,
            results: [{
              productId: "product-1",
              name: "Perfume Gold",
              imageUrl: "https://cdn.example.com/gold.png",
              barcode: "789",
              sku: "SKU-GOLD",
              warehouseId: "warehouse-1",
              warehouseName: "Centro",
              totalQuantity: 25,
              latestBatchSellingPrice: 12990,
              latestBatchCode: "NEW",
              latestBatchCreatedAt: "2026-02-01T10:00:00",
            }],
          },
        });
      },
    });

    const response = await client.searchProducts({ query: "gold", warehouseId: "warehouse-1", limit: 5 });

    expect(requests[0]?.headers.get("X-StockShift-Bot-Key")).toBe("bot-key");
    expect(requests[0]?.url).toContain("/api/internal/bot/products/search");
    expect(response.results[0]?.name).toBe("Perfume Gold");
  });

  it("throws a useful error for backend failures", async () => {
    const client = new StockShiftBotApiClient({
      baseUrl: "https://stockshift.example.com/stockshift",
      apiKey: "bot-key",
      fetcher: async () => new Response("unavailable", { status: 503 }),
    });

    await expect(client.listWarehouses()).rejects.toThrow("StockShift API request failed with status 503");
  });
});
```

- [ ] **Step 2: Write failing formatter tests**

Create `src/services/product-response-formatter.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { formatProductCaption } from "./product-response-formatter.js";

describe("formatProductCaption", () => {
  it("formats product details with BRL price", () => {
    expect(formatProductCaption({
      productId: "product-1",
      name: "Perfume Gold",
      imageUrl: "https://cdn.example.com/gold.png",
      barcode: "7891234567890",
      sku: "SKU-GOLD",
      warehouseId: "warehouse-1",
      warehouseName: "Centro",
      totalQuantity: 25,
      latestBatchSellingPrice: 12990,
      latestBatchCode: "NEW",
      latestBatchCreatedAt: "2026-02-01T10:00:00",
    })).toContain("Preco do ultimo lote: R$ 129,90");
  });

  it("uses fallback text when barcode and price are absent", () => {
    const caption = formatProductCaption({
      productId: "product-1",
      name: "Produto Sem Codigo",
      imageUrl: null,
      barcode: null,
      sku: null,
      warehouseId: "warehouse-1",
      warehouseName: "Centro",
      totalQuantity: 0,
      latestBatchSellingPrice: null,
      latestBatchCode: null,
      latestBatchCreatedAt: null,
    });

    expect(caption).toContain("Codigo de barras: sem codigo de barras");
    expect(caption).toContain("Preco do ultimo lote: sem preco cadastrado");
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `pnpm test src/clients/stockshift-bot-api-client.test.ts src/services/product-response-formatter.test.ts`

Expected: FAIL because client, formatter, and types do not exist.

- [ ] **Step 4: Create StockShift types**

Create `src/types/stockshift.ts`:

```ts
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

export interface StockShiftWarehouse {
  id: string;
  name: string;
  code: string;
  city: string;
  state: string;
}

export interface StockShiftProductSearchResult {
  productId: string;
  name: string;
  imageUrl: string | null;
  barcode: string | null;
  sku: string | null;
  warehouseId: string;
  warehouseName: string;
  totalQuantity: number;
  latestBatchSellingPrice: number | null;
  latestBatchCode: string | null;
  latestBatchCreatedAt: string | null;
}

export interface StockShiftProductSearchResponse {
  results: StockShiftProductSearchResult[];
  hasMore: boolean;
}

export interface ProductSearchInput {
  query: string;
  warehouseId: string;
  limit: number;
}
```

- [ ] **Step 5: Implement StockShift API client**

Create `src/clients/stockshift-bot-api-client.ts`:

```ts
import { ApiResponse, ProductSearchInput, StockShiftProductSearchResponse, StockShiftWarehouse } from "../types/stockshift.js";

export type Fetcher = (input: string | URL, init?: RequestInit) => Promise<Response>;

export interface StockShiftBotApiClientOptions {
  baseUrl: string;
  apiKey: string;
  fetcher?: Fetcher;
}

export class StockShiftBotApiClient {
  private readonly baseUrl: string;
  private readonly apiKey: string;
  private readonly fetcher: Fetcher;

  constructor(options: StockShiftBotApiClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.apiKey = options.apiKey;
    this.fetcher = options.fetcher ?? fetch;
  }

  async listWarehouses(): Promise<StockShiftWarehouse[]> {
    const response = await this.get<ApiResponse<StockShiftWarehouse[]>>("/api/internal/bot/warehouses");
    return response.data;
  }

  async searchWarehouses(query: string): Promise<StockShiftWarehouse[]> {
    const params = new URLSearchParams({ query });
    const response = await this.get<ApiResponse<StockShiftWarehouse[]>>(`/api/internal/bot/warehouses/search?${params}`);
    return response.data;
  }

  async searchProducts(input: ProductSearchInput): Promise<StockShiftProductSearchResponse> {
    const params = new URLSearchParams({
      query: input.query,
      warehouseId: input.warehouseId,
      limit: String(input.limit),
    });
    const response = await this.get<ApiResponse<StockShiftProductSearchResponse>>(`/api/internal/bot/products/search?${params}`);
    return response.data;
  }

  private async get<T>(path: string): Promise<T> {
    const response = await this.fetcher(`${this.baseUrl}${path}`, {
      headers: { "X-StockShift-Bot-Key": this.apiKey },
    });
    if (!response.ok) {
      throw new Error(`StockShift API request failed with status ${response.status}`);
    }
    const body: unknown = await response.json();
    return body as T;
  }
}
```

- [ ] **Step 6: Implement product formatter**

Create `src/services/product-response-formatter.ts`:

```ts
import { StockShiftProductSearchResult } from "../types/stockshift.js";

const brlFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

export function formatProductCaption(product: StockShiftProductSearchResult): string {
  return [
    `Produto: ${product.name}`,
    `Codigo de barras: ${product.barcode ?? "sem codigo de barras"}`,
    `SKU: ${product.sku ?? "sem SKU"}`,
    `Deposito: ${product.warehouseName}`,
    `Quantidade total: ${product.totalQuantity}`,
    `Preco do ultimo lote: ${formatPrice(product.latestBatchSellingPrice)}`,
    `Ultimo lote: ${product.latestBatchCode ?? "sem lote"}`,
  ].join("\n");
}

function formatPrice(priceInCents: number | null): string {
  if (priceInCents === null) {
    return "sem preco cadastrado";
  }
  return brlFormatter.format(priceInCents / 100);
}
```

- [ ] **Step 7: Run client and formatter tests**

Run: `pnpm test src/clients/stockshift-bot-api-client.test.ts src/services/product-response-formatter.test.ts`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
cd /home/suel/projects/stockshift/stockshift-telegram-bot
git add src/types/stockshift.ts src/clients/stockshift-bot-api-client.ts src/clients/stockshift-bot-api-client.test.ts src/services/product-response-formatter.ts src/services/product-response-formatter.test.ts
git commit -m "feat: add stockshift client and product formatting"
```

---

### Task 4: OpenAI Intent, Warehouse Resolution, And Product Query Orchestration

**Files:**
- Create: `stockshift-telegram-bot/src/types/intent.ts`
- Create: `stockshift-telegram-bot/src/clients/openai-bot-client.ts`
- Create: `stockshift-telegram-bot/src/services/warehouse-resolution-service.ts`
- Create: `stockshift-telegram-bot/src/services/product-query-service.ts`
- Test: `stockshift-telegram-bot/src/services/warehouse-resolution-service.test.ts`
- Test: `stockshift-telegram-bot/src/services/product-query-service.test.ts`

**Interfaces:**
- Consumes: `StockShiftBotApiClient`, `WarehousePreferencesRepository`, `formatProductCaption`, OpenAI API key/models.
- Produces: `OpenAiBotClient.transcribeAudio(input)`, `OpenAiBotClient.extractIntent(text)`, `resolveWarehouseForQuestion(input)`, and `ProductQueryService.answerProductQuestion(input)`.

- [ ] **Step 1: Write failing warehouse resolution tests**

Create `src/services/warehouse-resolution-service.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { resolveWarehouseForQuestion } from "./warehouse-resolution-service.js";
import { StockShiftWarehouse } from "../types/stockshift.js";

const warehouses: StockShiftWarehouse[] = [{
  id: "warehouse-1",
  name: "Centro",
  code: "CTR",
  city: "Sao Paulo",
  state: "SP",
}];

describe("resolveWarehouseForQuestion", () => {
  it("uses mentioned warehouse before stored default", async () => {
    const result = await resolveWarehouseForQuestion({
      telegramUserId: "123",
      warehouseName: "centro",
      defaultWarehouse: { telegramUserId: "123", warehouseId: "other", warehouseName: "Outro", updatedAt: "now" },
      searchWarehouses: async () => warehouses,
    });

    expect(result.status).toBe("resolved");
    expect(result.warehouse?.id).toBe("warehouse-1");
  });

  it("uses stored default when no warehouse is mentioned", async () => {
    const result = await resolveWarehouseForQuestion({
      telegramUserId: "123",
      warehouseName: null,
      defaultWarehouse: { telegramUserId: "123", warehouseId: "warehouse-2", warehouseName: "Norte", updatedAt: "now" },
      searchWarehouses: async () => [],
    });

    expect(result.status).toBe("resolved");
    expect(result.warehouse?.id).toBe("warehouse-2");
  });

  it("asks for configuration when no warehouse can be resolved", async () => {
    const result = await resolveWarehouseForQuestion({
      telegramUserId: "123",
      warehouseName: null,
      defaultWarehouse: null,
      searchWarehouses: async () => [],
    });

    expect(result.status).toBe("missing");
  });
});
```

- [ ] **Step 2: Write failing product query service tests**

Create `src/services/product-query-service.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { ProductQueryService } from "./product-query-service.js";
import { StockShiftProductSearchResponse, StockShiftWarehouse } from "../types/stockshift.js";

describe("ProductQueryService", () => {
  it("returns formatted product answers", async () => {
    const warehouse: StockShiftWarehouse = { id: "warehouse-1", name: "Centro", code: "CTR", city: "Sao Paulo", state: "SP" };
    const searchResponse: StockShiftProductSearchResponse = {
      hasMore: false,
      results: [{
        productId: "product-1",
        name: "Perfume Gold",
        imageUrl: "https://cdn.example.com/gold.png",
        barcode: "789",
        sku: "SKU-GOLD",
        warehouseId: "warehouse-1",
        warehouseName: "Centro",
        totalQuantity: 25,
        latestBatchSellingPrice: 12990,
        latestBatchCode: "NEW",
        latestBatchCreatedAt: "2026-02-01T10:00:00",
      }],
    };
    const service = new ProductQueryService({
      maxResults: 5,
      extractIntent: async () => ({ productQuery: "gold", warehouseName: "Centro", confidence: 0.9 }),
      getDefaultWarehouse: () => null,
      searchWarehouses: async () => [warehouse],
      searchProducts: async () => searchResponse,
    });

    const result = await service.answerProductQuestion({ telegramUserId: "123", text: "preco do gold" });

    expect(result.status).toBe("answered");
    expect(result.messages[0]?.caption).toContain("Perfume Gold");
    expect(result.messages[0]?.photoUrl).toBe("https://cdn.example.com/gold.png");
  });

  it("asks for clarification when intent confidence is low", async () => {
    const service = new ProductQueryService({
      maxResults: 5,
      extractIntent: async () => ({ productQuery: "", warehouseName: null, confidence: 0.3 }),
      getDefaultWarehouse: () => null,
      searchWarehouses: async () => [],
      searchProducts: async () => ({ results: [], hasMore: false }),
    });

    const result = await service.answerProductQuestion({ telegramUserId: "123", text: "quanto custa?" });

    expect(result.status).toBe("clarify");
    expect(result.messages[0]?.text).toContain("nome, SKU ou codigo de barras");
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `pnpm test src/services/warehouse-resolution-service.test.ts src/services/product-query-service.test.ts`

Expected: FAIL because intent and service files do not exist.

- [ ] **Step 4: Create intent types and OpenAI client**

Create `src/types/intent.ts`:

```ts
export interface ProductQuestionIntent {
  productQuery: string;
  warehouseName: string | null;
  confidence: number;
}

export interface AudioTranscriptionInput {
  buffer: Buffer;
  filename: string;
  mimeType: string;
}
```

Create `src/clients/openai-bot-client.ts`:

```ts
import OpenAI, { toFile } from "openai";
import { z } from "zod";
import { AudioTranscriptionInput, ProductQuestionIntent } from "../types/intent.js";

const intentSchema = z.object({
  productQuery: z.string(),
  warehouseName: z.string().nullable(),
  confidence: z.number().min(0).max(1),
});

export interface OpenAiBotClientOptions {
  apiKey: string;
  transcriptionModel: string;
  intentModel: string;
}

export class OpenAiBotClient {
  private readonly openai: OpenAI;

  constructor(private readonly options: OpenAiBotClientOptions) {
    this.openai = new OpenAI({ apiKey: options.apiKey });
  }

  async transcribeAudio(input: AudioTranscriptionInput): Promise<string> {
    const file = await toFile(input.buffer, input.filename, { type: input.mimeType });
    const response = await this.openai.audio.transcriptions.create({
      file,
      model: this.options.transcriptionModel,
    });
    return response.text;
  }

  async extractIntent(text: string): Promise<ProductQuestionIntent> {
    const response = await this.openai.chat.completions.create({
      model: this.options.intentModel,
      response_format: { type: "json_object" },
      messages: [{ role: "user", content: this.intentPrompt(text) }],
    });
    const content = response.choices[0]?.message.content ?? "{}";
    const parsed: unknown = JSON.parse(content);
    return intentSchema.parse(parsed);
  }

  private intentPrompt(text: string): string {
    return `Extraia JSON sem markdown com productQuery, warehouseName e confidence entre 0 e 1. Pergunta: ${text}`;
  }
}
```

- [ ] **Step 5: Implement warehouse resolution**

Create `src/services/warehouse-resolution-service.ts`:

```ts
import { WarehousePreference } from "../storage/warehouse-preferences-repository.js";
import { StockShiftWarehouse } from "../types/stockshift.js";

export type WarehouseResolution =
  | { status: "resolved"; warehouse: StockShiftWarehouse }
  | { status: "missing" };

export interface WarehouseResolutionInput {
  telegramUserId: string;
  warehouseName: string | null;
  defaultWarehouse: WarehousePreference | null;
  searchWarehouses: (query: string) => Promise<StockShiftWarehouse[]>;
}

export async function resolveWarehouseForQuestion(input: WarehouseResolutionInput): Promise<WarehouseResolution> {
  if (input.warehouseName !== null && input.warehouseName.trim().length > 0) {
    const matches = await input.searchWarehouses(input.warehouseName.trim());
    const warehouse = matches[0];
    return warehouse ? { status: "resolved", warehouse } : { status: "missing" };
  }
  if (input.defaultWarehouse !== null) {
    return {
      status: "resolved",
      warehouse: {
        id: input.defaultWarehouse.warehouseId,
        name: input.defaultWarehouse.warehouseName,
        code: "",
        city: "",
        state: "",
      },
    };
  }
  return { status: "missing" };
}
```

- [ ] **Step 6: Implement product query orchestration**

Create `src/services/product-query-service.ts`:

```ts
import { WarehousePreference } from "../storage/warehouse-preferences-repository.js";
import { ProductQuestionIntent } from "../types/intent.js";
import { ProductSearchInput, StockShiftProductSearchResponse, StockShiftWarehouse } from "../types/stockshift.js";
import { formatProductCaption } from "./product-response-formatter.js";
import { resolveWarehouseForQuestion } from "./warehouse-resolution-service.js";

export interface ProductAnswerMessage {
  text?: string;
  photoUrl?: string;
  caption?: string;
}

export interface ProductAnswerResult {
  status: "answered" | "clarify" | "missing_warehouse" | "not_found";
  messages: ProductAnswerMessage[];
}

export interface ProductQueryDependencies {
  maxResults: number;
  extractIntent: (text: string) => Promise<ProductQuestionIntent>;
  getDefaultWarehouse: (telegramUserId: string) => WarehousePreference | null;
  searchWarehouses: (query: string) => Promise<StockShiftWarehouse[]>;
  searchProducts: (input: ProductSearchInput) => Promise<StockShiftProductSearchResponse>;
}

export interface ProductQuestionInput {
  telegramUserId: string;
  text: string;
}

export class ProductQueryService {
  constructor(private readonly dependencies: ProductQueryDependencies) {}

  async answerProductQuestion(input: ProductQuestionInput): Promise<ProductAnswerResult> {
    const intent = await this.dependencies.extractIntent(input.text);
    if (intent.confidence < 0.6 || intent.productQuery.trim().length === 0) {
      return this.single("clarify", "Me envie o nome, SKU ou codigo de barras do produto.");
    }
    const warehouse = await this.resolveWarehouse(input.telegramUserId, intent.warehouseName);
    if (warehouse.status === "missing") {
      return this.single("missing_warehouse", "Escolha um deposito com /warehouse ou mencione o deposito na pergunta.");
    }
    const products = await this.dependencies.searchProducts({
      query: intent.productQuery,
      warehouseId: warehouse.warehouse.id,
      limit: this.dependencies.maxResults,
    });
    if (products.results.length === 0) {
      return this.single("not_found", "Nao encontrei produto. Tente nome, SKU ou codigo de barras.");
    }
    return this.toProductAnswer(products);
  }

  private async resolveWarehouse(telegramUserId: string, warehouseName: string | null) {
    return resolveWarehouseForQuestion({
      telegramUserId,
      warehouseName,
      defaultWarehouse: this.dependencies.getDefaultWarehouse(telegramUserId),
      searchWarehouses: this.dependencies.searchWarehouses,
    });
  }

  private toProductAnswer(products: StockShiftProductSearchResponse): ProductAnswerResult {
    const messages: ProductAnswerMessage[] = products.results.map((product) => {
      const caption = formatProductCaption(product);
      if (product.imageUrl !== null) {
        return { photoUrl: product.imageUrl, caption };
      }
      return { text: caption };
    });
    if (products.hasMore) {
      messages.push({ text: "Encontrei mais produtos. Refine a busca para ver os demais." });
    }
    return { status: "answered", messages };
  }

  private single(status: ProductAnswerResult["status"], text: string): ProductAnswerResult {
    return { status, messages: [{ text }] };
  }
}
```

- [ ] **Step 7: Run orchestration tests**

Run: `pnpm test src/services/warehouse-resolution-service.test.ts src/services/product-query-service.test.ts`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
cd /home/suel/projects/stockshift/stockshift-telegram-bot
git add src/types/intent.ts src/clients/openai-bot-client.ts src/services/warehouse-resolution-service.ts src/services/warehouse-resolution-service.test.ts src/services/product-query-service.ts src/services/product-query-service.test.ts
git commit -m "feat: add OpenAI intent and product query flow"
```

---

### Task 5: Telegram Handlers And Webhook Wiring

**Files:**
- Create: `stockshift-telegram-bot/src/bot/telegram-audio-downloader.ts`
- Create: `stockshift-telegram-bot/src/bot/telegram-bot.ts`
- Modify: `stockshift-telegram-bot/src/main.ts`
- Test: `stockshift-telegram-bot/src/bot/telegram-audio-downloader.test.ts`
- Test: `stockshift-telegram-bot/src/bot/telegram-bot.test.ts`

**Interfaces:**
- Consumes: `ProductQueryService`, `OpenAiBotClient.transcribeAudio`, `StockShiftBotApiClient`, `WarehousePreferencesRepository`, `createHttpServer`.
- Produces: Telegram `/start`, `/help`, `/warehouse`, warehouse callback selection, text question handling, voice/audio question handling, and live webhook registration.

- [ ] **Step 1: Write failing audio downloader test**

Create `src/bot/telegram-audio-downloader.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { TelegramAudioDownloader } from "./telegram-audio-downloader.js";

describe("TelegramAudioDownloader", () => {
  it("downloads Telegram file bytes into memory", async () => {
    const downloader = new TelegramAudioDownloader({
      botToken: "token",
      getFilePath: async () => "voice/file.oga",
      fetcher: async (input) => {
        expect(String(input)).toBe("https://api.telegram.org/file/bottoken/voice/file.oga");
        return new Response(new Uint8Array([1, 2, 3]));
      },
    });

    const audio = await downloader.downloadVoice("file-id");

    expect([...audio.buffer]).toEqual([1, 2, 3]);
    expect(audio.filename).toBe("telegram-voice.oga");
    expect(audio.mimeType).toBe("audio/ogg");
  });
});
```

- [ ] **Step 2: Write failing bot access test**

Create `src/bot/telegram-bot.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { isAllowedTelegramUser } from "./telegram-bot.js";

describe("isAllowedTelegramUser", () => {
  it("allows configured users", () => {
    expect(isAllowedTelegramUser("123", new Set(["123"]))).toBe(true);
  });

  it("rejects missing or unconfigured users", () => {
    expect(isAllowedTelegramUser(undefined, new Set(["123"]))).toBe(false);
    expect(isAllowedTelegramUser("999", new Set(["123"]))).toBe(false);
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `pnpm test src/bot/telegram-audio-downloader.test.ts src/bot/telegram-bot.test.ts`

Expected: FAIL because bot files do not exist.

- [ ] **Step 4: Implement Telegram audio downloader**

Create `src/bot/telegram-audio-downloader.ts`:

```ts
import { AudioTranscriptionInput } from "../types/intent.js";
import { Fetcher } from "../clients/stockshift-bot-api-client.js";

export interface TelegramAudioDownloaderOptions {
  botToken: string;
  getFilePath?: (fileId: string) => Promise<string>;
  fetcher?: Fetcher;
}

interface TelegramGetFileResponse {
  ok: boolean;
  result?: { file_path?: string };
}

export class TelegramAudioDownloader {
  private readonly fetcher: Fetcher;

  constructor(private readonly options: TelegramAudioDownloaderOptions) {
    this.fetcher = options.fetcher ?? fetch;
  }

  async downloadVoice(fileId: string): Promise<AudioTranscriptionInput> {
    const filePath = await this.resolveFilePath(fileId);
    const response = await this.fetcher(`https://api.telegram.org/file/bot${this.options.botToken}/${filePath}`);
    if (!response.ok) {
      throw new Error(`Telegram file download failed with status ${response.status}`);
    }
    return {
      buffer: Buffer.from(await response.arrayBuffer()),
      filename: "telegram-voice.oga",
      mimeType: "audio/ogg",
    };
  }

  private async resolveFilePath(fileId: string): Promise<string> {
    if (this.options.getFilePath !== undefined) {
      return this.options.getFilePath(fileId);
    }
    const params = new URLSearchParams({ file_id: fileId });
    const response = await this.fetcher(`https://api.telegram.org/bot${this.options.botToken}/getFile?${params}`);
    if (!response.ok) {
      throw new Error(`Telegram getFile failed with status ${response.status}`);
    }
    const body = await response.json() as TelegramGetFileResponse;
    const filePath = body.result?.file_path;
    if (body.ok !== true || filePath === undefined) {
      throw new Error(`Telegram file ${fileId} has no file_path`);
    }
    return filePath;
  }
}
```

- [ ] **Step 5: Implement Telegram bot factory**

Create `src/bot/telegram-bot.ts`:

```ts
import { Bot, Context, InlineKeyboard } from "grammy";
import { StockShiftBotApiClient } from "../clients/stockshift-bot-api-client.js";
import { OpenAiBotClient } from "../clients/openai-bot-client.js";
import { WarehousePreferencesRepository } from "../storage/warehouse-preferences-repository.js";
import { ProductQueryService } from "../services/product-query-service.js";
import { TelegramAudioDownloader } from "./telegram-audio-downloader.js";

export interface TelegramBotDependencies {
  token: string;
  allowedUserIds: Set<string>;
  stockShiftClient: StockShiftBotApiClient;
  openAiClient: OpenAiBotClient;
  preferencesRepository: WarehousePreferencesRepository;
  productQueryService: ProductQueryService;
  audioDownloader: TelegramAudioDownloader;
}

export function isAllowedTelegramUser(userId: string | undefined, allowedUserIds: Set<string>): boolean {
  return userId !== undefined && allowedUserIds.has(userId);
}

export function createTelegramBot(dependencies: TelegramBotDependencies): Bot {
  const bot = new Bot(dependencies.token);
  bot.use(async (context, next) => {
    const userId = context.from?.id === undefined ? undefined : String(context.from.id);
    if (!isAllowedTelegramUser(userId, dependencies.allowedUserIds)) {
      await context.reply("Acesso negado.");
      return;
    }
    await next();
  });
  bot.command("start", async (context) => context.reply("Envie audio ou texto perguntando por um produto. Use /warehouse para escolher o deposito padrao."));
  bot.command("help", async (context) => context.reply("Exemplos: 'preco do Perfume Gold no Centro' ou envie um audio com a pergunta."));
  bot.command("warehouse", async (context) => sendWarehouseChoices(context, dependencies));
  bot.callbackQuery(/^warehouse:(.+)$/, async (context) => saveWarehouseChoice(context, dependencies));
  bot.on("message:text", async (context) => answerTextQuestion(context, dependencies, context.message.text));
  bot.on(["message:voice", "message:audio"], async (context) => answerAudioQuestion(context, dependencies));
  return bot;
}

async function sendWarehouseChoices(context: Context, dependencies: TelegramBotDependencies): Promise<void> {
  const warehouses = await dependencies.stockShiftClient.listWarehouses();
  const keyboard = new InlineKeyboard();
  for (const warehouse of warehouses) {
    keyboard.text(warehouse.name, `warehouse:${warehouse.id}`).row();
  }
  await context.reply("Escolha o deposito padrao:", { reply_markup: keyboard });
}

async function saveWarehouseChoice(context: Context, dependencies: TelegramBotDependencies): Promise<void> {
  const warehouseId = context.match?.[1];
  const telegramUserId = context.from?.id === undefined ? null : String(context.from.id);
  if (warehouseId === undefined || telegramUserId === null) {
    await context.answerCallbackQuery("Nao foi possivel salvar o deposito.");
    return;
  }
  const warehouses = await dependencies.stockShiftClient.listWarehouses();
  const warehouse = warehouses.find((candidate) => candidate.id === warehouseId);
  if (warehouse === undefined) {
    await context.answerCallbackQuery("Deposito nao encontrado.");
    return;
  }
  dependencies.preferencesRepository.savePreference({ telegramUserId, warehouseId, warehouseName: warehouse.name });
  await context.answerCallbackQuery("Deposito salvo.");
  await context.reply(`Deposito padrao: ${warehouse.name}`);
}

async function answerTextQuestion(context: Context, dependencies: TelegramBotDependencies, text: string): Promise<void> {
  const telegramUserId = String(context.from?.id);
  try {
    const result = await dependencies.productQueryService.answerProductQuestion({ telegramUserId, text });
    await sendAnswerMessages(context, result.messages);
  } catch {
    await context.reply("StockShift esta temporariamente indisponivel. Tente novamente em alguns instantes.");
  }
}

async function answerAudioQuestion(context: Context, dependencies: TelegramBotDependencies): Promise<void> {
  const fileId = context.message?.voice?.file_id ?? context.message?.audio?.file_id;
  if (fileId === undefined) {
    await context.reply("Nao consegui ler o audio. Envie novamente ou escreva a pergunta.");
    return;
  }
  try {
    const audio = await dependencies.audioDownloader.downloadVoice(fileId);
    const text = await dependencies.openAiClient.transcribeAudio(audio);
    await answerTextQuestion(context, dependencies, text);
  } catch {
    await context.reply("Nao consegui transcrever o audio. Envie novamente ou escreva a pergunta.");
  }
}

async function sendAnswerMessages(context: Context, messages: Array<{ text?: string; photoUrl?: string; caption?: string }>): Promise<void> {
  for (const message of messages) {
    if (message.photoUrl !== undefined && message.caption !== undefined) {
      await context.replyWithPhoto(message.photoUrl, { caption: message.caption });
      continue;
    }
    await context.reply(message.text ?? message.caption ?? "Nao encontrei uma resposta para essa pergunta.");
  }
}
```

- [ ] **Step 6: Wire production main**

Replace `src/main.ts` with:

```ts
import { webhookCallback } from "grammy";
import { StockShiftBotApiClient } from "./clients/stockshift-bot-api-client.js";
import { OpenAiBotClient } from "./clients/openai-bot-client.js";
import { loadBotEnv } from "./config/env.js";
import { createTelegramBot } from "./bot/telegram-bot.js";
import { TelegramAudioDownloader } from "./bot/telegram-audio-downloader.js";
import { ProductQueryService } from "./services/product-query-service.js";
import { createHttpServer } from "./server/http-server.js";
import { createSqliteDatabase, migrateWarehousePreferences } from "./storage/sqlite-connection.js";
import { WarehousePreferencesRepository } from "./storage/warehouse-preferences-repository.js";

const env = loadBotEnv(process.env);
const database = createSqliteDatabase(env.SQLITE_PATH);
migrateWarehousePreferences(database);

const stockShiftClient = new StockShiftBotApiClient({
  baseUrl: env.STOCKSHIFT_API_BASE_URL,
  apiKey: env.STOCKSHIFT_BOT_API_KEY,
});
const openAiClient = new OpenAiBotClient({
  apiKey: env.OPENAI_API_KEY,
  transcriptionModel: env.OPENAI_TRANSCRIPTION_MODEL,
  intentModel: env.OPENAI_INTENT_MODEL,
});
const preferencesRepository = new WarehousePreferencesRepository(database);
const productQueryService = new ProductQueryService({
  maxResults: env.BOT_MAX_PRODUCT_RESULTS,
  extractIntent: (text) => openAiClient.extractIntent(text),
  getDefaultWarehouse: (telegramUserId) => preferencesRepository.getPreference(telegramUserId),
  searchWarehouses: (query) => stockShiftClient.searchWarehouses(query),
  searchProducts: (input) => stockShiftClient.searchProducts(input),
});
const bot = createTelegramBot({
  token: env.TELEGRAM_BOT_TOKEN,
  allowedUserIds: env.TELEGRAM_ALLOWED_USER_IDS,
  stockShiftClient,
  openAiClient,
  preferencesRepository,
  productQueryService,
  audioDownloader: new TelegramAudioDownloader({
    botToken: env.TELEGRAM_BOT_TOKEN,
  }),
});

await bot.api.setWebhook(env.BOT_PUBLIC_WEBHOOK_URL);

const webhookPath = new URL(env.BOT_PUBLIC_WEBHOOK_URL).pathname;
const server = createHttpServer({
  webhookPath,
  webhook: webhookCallback(bot, "http"),
});

server.listen(env.PORT, () => {
  console.log(JSON.stringify({ event: "telegram_bot_listening", port: env.PORT, webhookPath }));
});
```

- [ ] **Step 7: Run bot handler tests and build**

Run: `pnpm test src/bot/telegram-audio-downloader.test.ts src/bot/telegram-bot.test.ts && pnpm typecheck && pnpm build`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
cd /home/suel/projects/stockshift/stockshift-telegram-bot
git add src/bot src/main.ts
git commit -m "feat: wire telegram webhook handlers"
```

---

### Task 6: VPS Deployment Wiring And Documentation

**Files:**
- Modify: `stockshift-infra/docker-compose.prod.yml`
- Modify: `stockshift-infra/.env.production.example`
- Modify: `stockshift-infra/README.md`
- Modify: `vps-infra/Caddyfile`
- Modify: `vps-infra/README.md`
- Create: `stockshift-telegram-bot/README.md`

**Interfaces:**
- Consumes: built `stockshift-telegram-bot` Docker image and backend internal API.
- Produces: production compose service `stockshift-telegram-bot`, SQLite volume, Caddy webhook route, and operator docs.

- [ ] **Step 1: Update production compose**

Modify `stockshift-infra/docker-compose.prod.yml` by adding this service after `backend`:

```yaml
  telegram-bot:
    image: ${TELEGRAM_BOT_IMAGE:-ghcr.io/lexmarcos/stockshift-telegram-bot:latest}
    restart: unless-stopped
    env_file:
      - .env.production
    depends_on:
      backend:
        condition: service_healthy
    expose:
      - "3001"
    volumes:
      - telegram_bot_data:/data
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:3001/health | grep -q '\"status\":\"ok\"'"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 30s
    networks:
      web:
        aliases:
          - stockshift-telegram-bot
      stockshift-internal:
```

Add this under `volumes`:

```yaml
  telegram_bot_data:
```

- [ ] **Step 2: Update production env example**

Append to `stockshift-infra/.env.production.example`:

```dotenv
TELEGRAM_BOT_IMAGE=ghcr.io/lexmarcos/stockshift-telegram-bot:latest
PORT=3001
TELEGRAM_BOT_TOKEN=change-me
TELEGRAM_ALLOWED_USER_IDS=123456789,987654321
STOCKSHIFT_BOT_API_KEY=change-me
STOCKSHIFT_BOT_TENANT_ID=00000000-0000-0000-0000-000000000000
STOCKSHIFT_API_BASE_URL=https://stockshift.example.com/stockshift
BOT_PUBLIC_WEBHOOK_URL=https://stockshift.example.com/stockshift/telegram-bot/webhook
SQLITE_PATH=/data/telegram-bot.sqlite
BOT_MAX_PRODUCT_RESULTS=5
OPENAI_TRANSCRIPTION_MODEL=gpt-4o-mini-transcribe
OPENAI_INTENT_MODEL=gpt-4.1-nano
```

- [ ] **Step 3: Update Caddy route**

Modify `vps-infra/Caddyfile` so the StockShift site block becomes:

```caddyfile
{$STOCKSHIFT_DOMAIN} {
	encode zstd gzip
	import security_headers

	reverse_proxy /stockshift/telegram-bot/* stockshift-telegram-bot:3001
	reverse_proxy /stockshift/api/* stockshift-backend:8080
	reverse_proxy /stockshift/actuator/* stockshift-backend:8080
	reverse_proxy stockshift-frontend:3000
}
```

- [ ] **Step 4: Add bot README**

Create `stockshift-telegram-bot/README.md`:

````markdown
# StockShift Telegram Bot

Telegram bot for read-only product lookup through StockShift internal bot endpoints.

## Commands

- `/start`: explains usage.
- `/help`: shows examples.
- `/warehouse`: lets an allowed Telegram user pick a default warehouse.

## Required Environment

- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_ALLOWED_USER_IDS`
- `OPENAI_API_KEY`
- `STOCKSHIFT_API_BASE_URL`
- `STOCKSHIFT_BOT_API_KEY`
- `BOT_PUBLIC_WEBHOOK_URL`
- `SQLITE_PATH`

## Local Checks

```bash
pnpm install
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

## Privacy

The bot does not persist audio, transcriptions, questions, or answers. SQLite stores only the default warehouse per Telegram user ID.
````

- [ ] **Step 5: Update infra README files**

Append to `stockshift-infra/README.md`:

```markdown
## Telegram Bot

The `telegram-bot` service runs the StockShift Telegram bot on port `3001` and stores SQLite data in the `telegram_bot_data` volume. It joins the external `web` network with alias `stockshift-telegram-bot` so `vps-infra` can proxy `/stockshift/telegram-bot/*`.

Required additional secrets:

- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_ALLOWED_USER_IDS`
- `STOCKSHIFT_BOT_API_KEY`
- `STOCKSHIFT_BOT_TENANT_ID`
```

Append to `vps-infra/README.md` under the StockShift alias list:

```markdown
- `stockshift-telegram-bot:3001`
```

- [ ] **Step 6: Run bot and compose verification**

Run from `/home/suel/projects/stockshift/stockshift-telegram-bot`: `pnpm lint && pnpm typecheck && pnpm test && pnpm build && docker build -t stockshift-telegram-bot .`

Expected: PASS.

Run from `/home/suel/projects/stockshift/stockshift-infra`: `docker compose -f docker-compose.prod.yml config`

Expected: PASS with rendered `telegram-bot` service and `telegram_bot_data` volume.

Run from `/home/suel/projects/stockshift/vps-infra`: `docker compose config`

Expected: PASS with valid Caddy service config.

- [ ] **Step 7: Commit**

```bash
cd /home/suel/projects/stockshift/stockshift-telegram-bot
git add README.md
git commit -m "docs: document telegram bot service"

cd /home/suel/projects/stockshift/stockshift-infra
git add docker-compose.prod.yml .env.production.example README.md
git commit -m "feat(bot): add telegram bot deployment"

cd /home/suel/projects/stockshift/vps-infra
git add Caddyfile README.md
git commit -m "feat(bot): proxy telegram webhook"
```

---

## Self-Review Checklist

- Spec coverage: text questions, audio questions, OpenAI transcription, OpenAI intent extraction, allowlist access, SQLite warehouse defaults, StockShift internal API calls, response formatting, no content persistence, and VPS webhook deployment are covered.
- Red-flag scan: this plan uses concrete paths, concrete env names, concrete commands, concrete models, and concrete route names.
- Type consistency: `StockShiftProductSearchResponse`, `ProductQuestionIntent`, `WarehousePreferencesRepository`, `ProductQueryService`, and `TelegramAudioDownloader` signatures match across tasks.
- Execution dependency: Task 3 requires the backend internal API from the backend plan for live verification; tests use fake fetchers before the backend is live.
