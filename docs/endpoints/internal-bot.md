# Internal Bot Endpoints

These endpoints are for the Telegram product query bot. They are authenticated with `X-StockShift-Bot-Key` and are not public user-facing API endpoints.

**Base URL:** `/api/internal/bot`

**Authentication:** `X-StockShift-Bot-Key: change-me`

## GET /api/internal/bot/warehouses

Returns active warehouses for the configured bot tenant.

```json
{
  "success": true,
  "data": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "name": "Centro",
      "code": "CTR",
      "city": "Sao Paulo",
      "state": "SP"
    }
  ]
}
```

## GET /api/internal/bot/warehouses/search?query=centro

Searches active warehouses by name or code for the configured bot tenant.

## GET /api/internal/bot/products/search

Query parameters:

- `query`: product name, SKU, or barcode.
- `warehouseId`: UUID of the selected warehouse.
- `limit`: maximum result count. Defaults to `5` and is capped at `10`.

The endpoint returns product matches that have non-deleted batches in the selected warehouse. `totalQuantity` is summed across non-deleted batches. Latest batch price uses `createdAt DESC, id DESC`.

```json
{
  "success": true,
  "data": {
    "results": [
      {
        "productId": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Perfume Gold",
        "imageUrl": "https://cdn.example.com/products/gold.png",
        "barcode": "7891234567890",
        "sku": "SKU-GOLD",
        "warehouseId": "660e8400-e29b-41d4-a716-446655440001",
        "warehouseName": "Centro",
        "totalQuantity": 25,
        "latestBatchSellingPrice": 12990,
        "latestBatchCode": "NEW",
        "latestBatchCreatedAt": "2026-02-01T10:00:00"
      }
    ],
    "hasMore": false
  }
}
```
