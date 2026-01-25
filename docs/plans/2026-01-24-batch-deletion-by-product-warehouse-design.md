# Batch Deletion by Product and Warehouse - Design Document

**Date:** 2026-01-24
**Feature:** Delete all batches of a product within a warehouse
**Type:** New Endpoint

## Overview

This design adds a new endpoint to delete all batches of a specific product within a specific warehouse using soft-delete mechanism. This provides a bulk deletion operation scoped to a warehouse-product combination, maintaining data history for auditing purposes.

## Endpoint Design

### New Endpoint
```
DELETE /api/warehouses/{warehouseId}/products/{productId}/batches
```

### Authorization
- Required permissions: `BATCH_DELETE` or `ROLE_ADMIN`
- Consistent with existing batch delete endpoint

### Request
- **Path Variables:**
  - `warehouseId` (UUID) - The warehouse ID
  - `productId` (UUID) - The product ID
- **No request body required**
- **Tenant isolation:** Automatic via `TenantContext`

### Response

**Success (200 OK):**
```json
{
  "message": "Successfully deleted 5 batches",
  "deletedCount": 5,
  "productId": "uuid-here",
  "warehouseId": "uuid-here"
}
```

**Error Responses:**
- `404 NOT_FOUND` - Warehouse or product doesn't exist for tenant
- `403 FORBIDDEN` - Insufficient permissions
- `500 INTERNAL_SERVER_ERROR` - Unexpected error

**Important:** Returns 200 with `deletedCount: 0` if no batches exist (not 404), since the operation succeeded.

## Architecture Changes

### 1. Entity Layer

**File:** `src/main/java/br/com/stockshift/model/entity/Batch.java`

Add soft-delete support:

```java
@Entity
@Table(name = "batches")
@Where(clause = "deleted_at IS NULL")  // Auto-filter soft-deleted records
public class Batch extends TenantAwareEntity {

    // ... existing fields

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ... getters/setters
}
```

**Impact:** The `@Where` annotation ensures all existing queries automatically exclude soft-deleted batches without code changes.

### 2. Repository Layer

**File:** `src/main/java/br/com/stockshift/repository/BatchRepository.java`

Add new method:

```java
@Modifying
@Query("UPDATE Batch b SET b.deletedAt = CURRENT_TIMESTAMP " +
       "WHERE b.productId = :productId " +
       "AND b.warehouseId = :warehouseId " +
       "AND b.tenantId = :tenantId " +
       "AND b.deletedAt IS NULL")
int softDeleteByProductAndWarehouse(
    @Param("productId") UUID productId,
    @Param("warehouseId") UUID warehouseId,
    @Param("tenantId") UUID tenantId
);
```

**Design Decisions:**
- Single UPDATE query (efficient, atomic operation)
- Returns count of affected rows
- `deletedAt IS NULL` prevents double-deletion
- Tenant isolation enforced in query

### 3. Service Layer

**File:** `src/main/java/br/com/stockshift/service/BatchService.java`

Add new method and inject ProductRepository:

```java
// Add new dependency
private final ProductRepository productRepository;

@Transactional
public BatchDeletionResponse deleteAllByProductAndWarehouse(
    UUID warehouseId,
    UUID productId
) {
    UUID tenantId = TenantContext.getTenantId();

    // Validate warehouse exists and belongs to tenant
    warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Warehouse", "id", warehouseId));

    // Validate product exists and belongs to tenant
    productRepository.findByTenantIdAndId(tenantId, productId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Product", "id", productId));

    // Soft delete all batches
    int deletedCount = batchRepository.softDeleteByProductAndWarehouse(
        productId, warehouseId, tenantId);

    log.info("Soft deleted {} batches for product {} in warehouse {} for tenant {}",
        deletedCount, productId, warehouseId, tenantId);

    return new BatchDeletionResponse(
        "Successfully deleted " + deletedCount + " batches",
        deletedCount,
        productId,
        warehouseId
    );
}
```

**Validations:**
1. Warehouse existence check (prevents silent failure)
2. Product existence check (prevents silent failure)
3. Tenant isolation at every level
4. Transaction ensures atomicity

### 4. DTO Layer

**New File:** `src/main/java/br/com/stockshift/dto/warehouse/BatchDeletionResponse.java`

```java
package br.com.stockshift.dto.warehouse;

import java.util.UUID;

public record BatchDeletionResponse(
    String message,
    Integer deletedCount,
    UUID productId,
    UUID warehouseId
) {}
```

### 5. Controller Layer

**File:** `src/main/java/br/com/stockshift/controller/BatchController.java`

Add new endpoint:

```java
@DeleteMapping("/warehouses/{warehouseId}/products/{productId}/batches")
@PreAuthorize("hasAnyAuthority('BATCH_DELETE', 'ROLE_ADMIN')")
public ResponseEntity<BatchDeletionResponse> deleteAllBatchesByProductAndWarehouse(
    @PathVariable UUID warehouseId,
    @PathVariable UUID productId
) {
    log.info("Request to delete all batches for product {} in warehouse {}",
        productId, warehouseId);

    BatchDeletionResponse response = batchService.deleteAllByProductAndWarehouse(
        warehouseId, productId);

    return ResponseEntity.ok(response);
}
```

## Soft-Delete Implementation

### Why Soft-Delete?

1. **Data History:** Preserves batch records for auditing
2. **Recovery:** Allows potential restoration of accidentally deleted batches
3. **Reporting:** Historical reports can still access deleted batch data
4. **Compliance:** Many regulations require audit trails

### How It Works

1. Database already has `deleted_at TIMESTAMPTZ` column (from V5 migration)
2. Instead of `DELETE FROM batches`, we `UPDATE batches SET deleted_at = NOW()`
3. Hibernate `@Where` annotation filters deleted records automatically
4. Existing endpoints unaffected (won't see soft-deleted batches)

### Database Impact

- No migration needed (column exists)
- Soft-deleted records remain in database
- Future consideration: Periodic cleanup job for old soft-deleted records

## Error Handling

All exceptions handled by existing `@ControllerAdvice`:

- `ResourceNotFoundException` → 404 with error details
- Generic exceptions → 500 with sanitized message

## Testing Strategy

### Integration Tests

**New File:** `src/test/java/br/com/stockshift/controller/BatchDeletionIntegrationTest.java`

Test cases:
1. **Successful deletion:**
   - Create warehouse, product, and 3 batches
   - Call DELETE endpoint
   - Verify `deletedCount = 3`
   - Verify batches have `deletedAt` timestamp
   - Verify GET /api/batches returns empty list

2. **Warehouse not found:**
   - Call with non-existent warehouse ID
   - Expect 404 NOT_FOUND

3. **Product not found:**
   - Call with non-existent product ID
   - Expect 404 NOT_FOUND

4. **No batches exist:**
   - Call with valid IDs but no batches
   - Expect 200 OK with `deletedCount = 0`

5. **Tenant isolation:**
   - Create batches for different tenant
   - Call endpoint as different tenant
   - Verify count = 0 (can't delete other tenant's batches)

6. **Authorization:**
   - Call without BATCH_DELETE permission
   - Expect 403 FORBIDDEN

7. **Soft-delete filter verification:**
   - Soft delete batches
   - Call GET /api/batches/warehouse/{id}
   - Verify deleted batches not returned

### Unit Tests

**Update:** `src/test/java/br/com/stockshift/service/BatchServiceTest.java`

Test cases:
1. Mock repository to return deletion count
2. Verify warehouse validation called
3. Verify product validation called
4. Verify correct response returned

## Security Considerations

1. **Tenant Isolation:** All queries filter by `tenantId`
2. **Authorization:** Requires `BATCH_DELETE` or `ROLE_ADMIN`
3. **Input Validation:** UUID path variables validated by Spring
4. **No Injection Risk:** Parameterized queries prevent SQL injection

## Performance Considerations

1. **Single Query:** Bulk update more efficient than individual deletes
2. **Index Usage:** Existing indexes on `product_id`, `warehouse_id`, `tenant_id`
3. **Transaction Scope:** Minimal (one UPDATE query)
4. **No Memory Loading:** UPDATE query doesn't load entities into memory

## Backward Compatibility

1. **Existing Endpoints:** Unaffected by `@Where` annotation
2. **Database Schema:** No migration required
3. **Existing Tests:** Should continue passing (deleted batches filtered)

## Future Enhancements

1. **Batch Recovery:** Add endpoint to restore soft-deleted batches
2. **Cleanup Job:** Periodic hard-delete of old soft-deleted records
3. **Audit Log:** Track who deleted batches and when
4. **Batch Deletion History:** UI to view deleted batches

## Implementation Checklist

- [ ] Add `deletedAt` field to Batch entity
- [ ] Add `@Where` annotation to Batch entity
- [ ] Add `softDeleteByProductAndWarehouse` method to BatchRepository
- [ ] Create `BatchDeletionResponse` DTO
- [ ] Inject `ProductRepository` into BatchService
- [ ] Add `deleteAllByProductAndWarehouse` method to BatchService
- [ ] Add DELETE endpoint to BatchController
- [ ] Create integration tests
- [ ] Update unit tests
- [ ] Manual testing with Postman/curl
- [ ] Update API documentation

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Accidental mass deletion | High | Require explicit productId + warehouseId (no wildcard) |
| Soft-delete filter missed | Medium | `@Where` annotation ensures automatic filtering |
| Performance on large datasets | Low | Single UPDATE query, indexed columns |
| Disk space growth | Low | Future cleanup job for old records |

## Success Criteria

1. Endpoint successfully deletes all matching batches
2. Deleted batches have `deletedAt` timestamp
3. Deleted batches filtered from all queries
4. Tenant isolation maintained
5. All tests passing
6. No performance degradation
