# Telegram Product Query Bot Design

## Context

StockShift already exposes a Spring Boot REST API under `/stockshift/api/**` with tenant-aware product, warehouse, batch, and OpenAI integrations. The Telegram bot will be a separate TypeScript/Node service hosted on the VPS. It will serve a small allowlist of Telegram users and answer read-only product questions by text or audio.

The MVP focuses on product lookup. A user asks about a product, optionally naming a warehouse. The bot responds with product name, image, barcode, latest batch selling price by `createdAt`, and total quantity across all existing batches in the selected warehouse.

## Goals

- Accept Telegram text and voice/audio questions.
- Restrict access to configured Telegram user IDs.
- Transcribe audio with OpenAI and discard the audio afterward.
- Use OpenAI to extract structured intent from text: product query and optional warehouse name.
- Support one StockShift tenant with multiple warehouses.
- Store each authorized user's default warehouse in SQLite.
- Query StockShift through an internal API key, not a StockShift user login.
- Return all product matches found, with a configurable maximum result count.
- Avoid persisting audio, transcriptions, user questions, or bot answers.

## Non-Goals

- Creating, updating, or deleting StockShift data from Telegram.
- Multi-tenant Telegram mapping.
- A Telegram admin UI for managing allowed users.
- Storing conversation history or audit transcripts.
- Replacing normal JWT authentication for existing StockShift API clients.

## Architecture

The system has two deployable parts:

1. StockShift backend additions.
2. A separate Telegram bot service.

The backend remains the source of truth for products, batches, warehouses, tenant filtering, and product aggregation. The bot service owns Telegram webhook handling, allowlist checks, OpenAI transcription/intent extraction, warehouse preference storage, and response formatting.

The bot never reads the StockShift database directly. It calls internal backend endpoints over HTTPS using `X-StockShift-Bot-Key`.

## Backend Design

### Bot API Key Authentication

Add an internal authentication path for bot-only endpoints. Requests include:

```http
X-StockShift-Bot-Key: <secret>
```

The backend compares the key against configuration. If valid, it establishes a bot request context with a configured tenant ID. This authentication applies only to internal bot routes, such as `/api/internal/bot/**`. Normal API endpoints continue to require JWT and permissions.

Configuration:

- `STOCKSHIFT_BOT_API_KEY`: shared secret accepted by the backend.
- `STOCKSHIFT_BOT_TENANT_ID`: tenant ID used for bot queries.

Invalid or missing keys return `401 Unauthorized`. Valid keys on unsupported routes must not bypass JWT security.

### Warehouse Endpoints

Add internal routes for warehouse resolution:

```http
GET /api/internal/bot/warehouses
GET /api/internal/bot/warehouses/search?query=<name>
```

Responses contain active tenant warehouses with:

- `id`
- `name`
- `code`
- `city`
- `state`

The bot uses these endpoints for `/warehouse` and to resolve a warehouse name extracted from a question.

### Product Query Endpoint

Add one aggregate endpoint:

```http
GET /api/internal/bot/products/search?query=<text>&warehouseId=<uuid>&limit=<number>
```

`limit` defaults to `5` and is capped at `10`. The endpoint searches products by name, SKU, or barcode within the configured tenant and returns matches that have non-deleted batches in the selected warehouse.

The response data contains:

- `results`: product match list.
- `hasMore`: whether more matches exist beyond `limit`.

For each product result, it returns:

- `productId`
- `name`
- `imageUrl`
- `barcode`
- `sku`
- `warehouseId`
- `warehouseName`
- `totalQuantity`
- `latestBatchSellingPrice`
- `latestBatchCode`
- `latestBatchCreatedAt`

`totalQuantity` is the sum of quantities for all non-deleted batches for that product in the selected warehouse. `latestBatchSellingPrice` comes from the non-deleted batch for that product and warehouse ordered by `createdAt DESC, id DESC`. Quantity does not affect latest-batch selection in the MVP.

If no product matches, the endpoint returns an empty list with `200 OK`.

## Bot Service Design

### Runtime

Use TypeScript/Node with `grammY`. The bot runs as an HTTP server on the VPS and receives Telegram updates through a webhook hosted behind the existing HTTPS domain.

Recommended deployment is Docker Compose with:

- A persistent volume for SQLite.
- Environment-based secrets.
- A healthcheck endpoint.
- Restart policy for process resilience.

### Configuration

Required environment variables:

- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_ALLOWED_USER_IDS`
- `OPENAI_API_KEY`
- `STOCKSHIFT_API_BASE_URL`
- `STOCKSHIFT_BOT_API_KEY`
- `BOT_PUBLIC_WEBHOOK_URL`
- `SQLITE_PATH`

Optional environment variables:

- `BOT_MAX_PRODUCT_RESULTS`, default `5`.
- `OPENAI_TRANSCRIPTION_MODEL`, default `gpt-4o-mini-transcribe`.
- `OPENAI_INTENT_MODEL`, default `gpt-4.1-nano`.

### SQLite Storage

The bot owns one table for warehouse preferences:

```sql
telegram_user_warehouse_preferences(
  telegram_user_id TEXT PRIMARY KEY,
  warehouse_id TEXT NOT NULL,
  warehouse_name TEXT NOT NULL,
  updated_at TEXT NOT NULL
)
```

No user questions, transcriptions, audio files, or answers are persisted.

### Commands

- `/start`: confirms access and explains how to ask product questions.
- `/help`: shows examples for text and audio questions.
- `/warehouse`: lists available warehouses and lets the user choose a default.

Warehouse selection is stored per `telegramUserId`. A warehouse mentioned in a question overrides the stored default for that query only.

## Message Flow

1. Telegram sends a webhook update to the bot service.
2. The bot extracts `telegramUserId` and verifies it against `TELEGRAM_ALLOWED_USER_IDS`.
3. Unauthorized users receive a short access-denied response.
4. Text messages use their text directly.
5. Voice/audio messages are downloaded from Telegram, sent to OpenAI for transcription, and discarded after transcription.
6. The bot sends the text to OpenAI for structured extraction.
7. OpenAI returns JSON with `productQuery`, optional `warehouseName`, and `confidence`.
8. If `warehouseName` exists, the bot resolves it through the backend warehouse endpoint.
9. If no warehouse is in the message, the bot uses the user's SQLite default warehouse.
10. If no warehouse can be resolved, the bot asks the user to use `/warehouse` or mention a warehouse in the question.
11. The bot calls the internal product query endpoint with `query`, `warehouseId`, and `limit`.
12. The bot formats all returned product matches up to `BOT_MAX_PRODUCT_RESULTS`.
13. If a product has `imageUrl`, the bot sends a Telegram photo with caption. Otherwise, it sends a text message.
14. If more matches exist than the configured maximum, the bot asks the user to refine the search.

## OpenAI Intent Contract

The intent extraction prompt must request JSON only:

```json
{
  "productQuery": "product name, sku, or barcode",
  "warehouseName": "optional warehouse name or null",
  "confidence": 0.0
}
```

If `productQuery` is empty or confidence is below `0.60`, the bot asks the user to rephrase with a product name, SKU, or barcode. The behavior must favor clarification over guessing.

## Response Format

Each product response should include:

- Product name.
- Barcode, or `sem codigo de barras` when absent.
- SKU when available.
- Warehouse name.
- Total quantity in the selected warehouse.
- Latest batch selling price formatted as BRL when present.
- Latest batch code and creation timestamp when useful.

Example caption:

```text
Produto: Perfume X
Codigo de barras: 7891234567890
SKU: PRD-123
Deposito: Centro
Quantidade total: 18
Preco do ultimo lote: R$ 129,90
Ultimo lote: BATCH-2026-001
```

## Error Handling

- Unauthorized Telegram user: reply with access denied and log only `telegramUserId`.
- Audio transcription failure: ask the user to retry in text.
- Intent extraction failure: ask for product name, SKU, or barcode.
- No warehouse selected: ask user to configure `/warehouse` or mention a warehouse.
- Warehouse not found: ask user to check the warehouse name or use `/warehouse`.
- No product found: suggest searching by name, SKU, or barcode.
- Backend unavailable: reply that StockShift is temporarily unavailable.
- Telegram send failure: log technical metadata only.

## Security And Privacy

- The Telegram allowlist is the first access control layer.
- The backend bot API key is the second access control layer.
- Bot API key access is restricted to `/api/internal/bot/**`.
- The backend applies one configured tenant ID to bot requests.
- Audio files are discarded immediately after transcription.
- Transcriptions, questions, and answers are not persisted.
- Logs avoid message content, transcription content, and product details.
- Logs may include `telegramUserId`, message type, latency, backend status, and result count.

## Testing Strategy

### Backend

- API key accepts valid bot requests.
- Missing or invalid API key rejects internal routes.
- Bot API key does not authenticate normal JWT routes.
- Warehouse list/search is tenant-scoped.
- Product search matches name, SKU, and barcode.
- Product query sums all batch quantities for the selected warehouse.
- Latest batch price uses the greatest `createdAt`.
- Empty matches return `200 OK` with an empty list.

### Bot

- Allowlist accepts and rejects expected Telegram IDs.
- Text and audio messages converge into the same query flow.
- OpenAI transcription and intent clients are mocked behind project-owned interfaces.
- Warehouse default is read and written through SQLite.
- Mentioned warehouse overrides SQLite default for one query.
- Product responses are formatted correctly with and without image URL.
- Expected error states return safe user-facing messages.

## Deployment

The bot deploys on the VPS behind the existing HTTPS domain. Docker Compose is recommended:

- `stockshift-telegram-bot` container.
- Environment variables injected from VPS secrets.
- SQLite file mounted on a persistent volume.
- Healthcheck route such as `GET /health`.
- Telegram webhook configured to `BOT_PUBLIC_WEBHOOK_URL`.

The backend deploy must include the new bot API key and bot tenant ID environment variables. The Telegram webhook should be configured only after the bot service is reachable over HTTPS.

## Acceptance Criteria

- Authorized Telegram users can ask product questions by text.
- Authorized Telegram users can ask product questions by audio.
- Unauthorized Telegram users cannot query StockShift data.
- A user can set a default warehouse with `/warehouse`.
- A warehouse mentioned in the question overrides the default warehouse for that request.
- The bot returns all product matches up to the configured maximum.
- Each returned match includes product name, image when present, barcode, latest batch selling price, and total quantity in the selected warehouse.
- No audio, transcription, question, or answer content is persisted.
- Backend tests and bot tests cover the critical success and failure paths.
