# Bulk Update Batch Selling Price

**Date:** 2026-06-23  
**Branch:** feat/analyze-image-prompt  
**Status:** design approved

## Goal

Allow changing the `sellingPrice` of all batches belonging to a product in a specific warehouse via a single API call.

## Scope

- Update `sellingPrice` only (not `costPrice`)
- Single warehouse (not cross-warehouse)
- Summary audit record (one audit entry with affected count), not per-batch auditing
- No database migration needed — `selling_price` column already exists

## Design

### New files

| File | Purpose |
|------|---------|
| `dto/warehouse/BatchSellingPriceUpdateRequest.java` | Request body: `sellingPrice` (Long, cents) |
| `dto/warehouse/BatchSellingPriceUpdateResponse.java` | Response: `message`, `affectedCount`, `productId`, `warehouseId` |

### Modified files

| File | Change |
|------|--------|
| `repository/BatchRepository.java` | New `@Modifying` query: `updateSellingPriceByProductAndWarehouse` |
| `service/BatchService.java` | New method: `updateSellingPriceByProductAndWarehouse` |
| `controller/BatchController.java` | New endpoint: `PATCH .../batches/selling-price` |

### Endpoint

```
PATCH /api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price
Authorization: Bearer <token>
Permission: batches:update + warehouse access
```

**Request body:**
```json
{ "sellingPrice": 1575 }
```

**Response (200):**
```json
{
  "success": true,
  "message": "Selling price updated for 3 batches",
  "data": {
    "message": "Selling price updated for 3 batches",
    "affectedCount": 3,
    "productId": "...",
    "warehouseId": "..."
  }
}
```

### Service flow

1. Extract `tenantId` from `TenantContext`
2. Validate warehouse access via `warehouseAccessService.validateWarehouseAccess(warehouseId)`
3. Validate warehouse exists and belongs to tenant (404 if not)
4. Validate product exists and belongs to tenant (404 if not)
5. Execute bulk JPQL update — single `UPDATE` query, returns affected row count
6. Record summary audit event (`BATCHES_SELLING_PRICE_UPDATED`) with metadata: `affectedCount`, `newSellingPrice`, `productId`, `warehouseId`
7. Return `BatchSellingPriceUpdateResponse`

### Repository query

```java
@Modifying
@Query("UPDATE Batch b SET b.sellingPrice = :sellingPrice " +
    "WHERE b.product.id = :productId " +
    "AND b.warehouse.id = :warehouseId " +
    "AND b.tenantId = :tenantId " +
    "AND b.deletedAt IS NULL")
int updateSellingPriceByProductAndWarehouse(...);
```

### Error handling

| Scenario | Error | HTTP |
|----------|-------|------|
| Warehouse not found / wrong tenant | `ResourceNotFoundException` | 404 |
| Product not found / wrong tenant | `ResourceNotFoundException` | 404 |
| No warehouse access | `UnauthorizedException` | 403 |
| `sellingPrice` null or negative | `MethodArgumentNotValidException` | 400 |
| No batches match (product has no batches in warehouse) | Success, `affectedCount = 0` | 200 |

### Tests

Following `BatchDeletionIntegrationTest` patterns:

- `updateSellingPrice_shouldUpdateAllBatchesForProductInWarehouse` — creates 3 batches, calls endpoint, verifies `affectedCount = 3` and each batch updated
- `updateSellingPrice_shouldReturnZeroWhenNoBatchesExist` — product exists but no batches → `affectedCount = 0`, 200
- `updateSellingPrice_shouldReturn403_whenNoWarehouseAccess` — user without access → 403
- `updateSellingPrice_shouldReturn404_whenProductNotFound` — nonexistent product → 404
- `updateSellingPrice_shouldReturn404_whenWarehouseNotFound` — nonexistent warehouse → 404
- `updateSellingPrice_shouldReturn400_whenNegativePrice` — negative price → 400
- `updateSellingPrice_shouldReturn400_whenNullPrice` — null price → 400

### What does NOT change

- No database migration
- No new permissions (reuses `batches:update`)
- No domain model changes
- No changes to `Product` entity

## Rationale

**Approach C (Bulk JPQL update + summary audit)** was chosen because:
1. Follows the existing `deleteAllByProductAndWarehouse` pattern in the codebase
2. Single SQL query — performant even with many batches
3. Summary audit is sufficient for a price change (unlike inventory quantity, selling price changes rarely conflict)
4. Minimal new code (~80 lines across 5 files)
