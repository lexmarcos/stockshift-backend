# Warehouse Products Endpoint - Design Document

**Date:** 2026-01-04
**Status:** Approved

## Overview

Add endpoint to retrieve products with aggregated stock for a specific warehouse. Returns paginated list of products with total quantity (sum of all batches).

## Requirements

- **Endpoint:** `GET /api/warehouses/{warehouseId}/products`
- **Return:** Products with aggregated stock quantity
- **Include:** Products with stock = 0 (historical products)
- **Format:** Paginated response
- **Permission:** `WAREHOUSE_READ`

## Architecture

### 1. Endpoint Structure

```
GET /api/warehouses/{warehouseId}/products?page=0&size=20&sort=name,asc
```

**Parameters:**
- Path: `warehouseId` (UUID)
- Query: `page`, `size`, `sort` (Spring Data standard)

**Response:** `Page<ProductWithStockResponse>`

**Flow:**
1. Controller receives request with warehouseId
2. Validates warehouse exists and belongs to tenant
3. Service calls repository with aggregation query
4. Repository executes query that:
   - JOINs Batch with Product
   - Filters by warehouseId and tenantId
   - Groups by product (GROUP BY product_id)
   - Sums quantities (SUM(quantity))
   - Returns products + total stock
5. Maps to response DTO
6. Returns paginated Page

### 2. Response DTO

**ProductWithStockResponse.java**

```java
public record ProductWithStockResponse(
    UUID id,
    String name,
    String sku,
    String barcode,
    BarcodeType barcodeType,
    String description,
    CategoryResponse category,
    BrandResponse brand,
    Boolean isKit,
    Map<String, Object> attributes,
    Boolean hasExpiration,
    Boolean active,
    BigDecimal totalQuantity  // Aggregated stock
) {}
```

**Design decisions:**
- Includes all relevant fields from existing `ProductResponse`
- Adds `totalQuantity` - sum of all batch quantities
- Uses `BigDecimal` for quantity (matches Batch entity)
- Includes populated category and brand (useful for future filters)
- Follows project's record pattern

### 3. Repository Query

**BatchRepository.java - new method:**

```java
@Query("""
    SELECT new br.com.stockshift.dto.warehouse.ProductWithStockProjection(
        p.id,
        p.name,
        p.sku,
        p.barcode,
        p.barcodeType,
        p.description,
        p.category,
        p.brand,
        p.isKit,
        p.attributes,
        p.hasExpiration,
        p.active,
        COALESCE(SUM(b.quantity), 0)
    )
    FROM Batch b
    JOIN b.product p
    WHERE b.warehouse.id = :warehouseId
      AND b.tenantId = :tenantId
      AND p.deletedAt IS NULL
    GROUP BY p.id, p.name, p.sku, p.barcode, p.barcodeType,
             p.description, p.category, p.brand, p.isKit,
             p.attributes, p.hasExpiration, p.active
    """)
Page<ProductWithStockProjection> findProductsWithStockByWarehouse(
    @Param("warehouseId") UUID warehouseId,
    @Param("tenantId") UUID tenantId,
    Pageable pageable
);
```

**Key points:**
- Uses `ProductWithStockProjection` (interface or class) for results
- `COALESCE(SUM(b.quantity), 0)` ensures returns 0 if no batches
- Filters soft-deleted products (`p.deletedAt IS NULL`)
- GROUP BY includes all non-aggregated fields
- Pageable enables native sorting and pagination

### 4. Service Layer

**WarehouseService.java - new method:**

```java
@Transactional(readOnly = true)
public Page<ProductWithStockResponse> getProductsWithStock(UUID warehouseId, Pageable pageable) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    // Validate warehouse exists and belongs to tenant
    warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)
        .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));

    // Fetch products with aggregated stock
    Page<ProductWithStockProjection> projections =
        batchRepository.findProductsWithStockByWarehouse(warehouseId, tenantId, pageable);

    // Map to response DTO
    return projections.map(this::mapToResponse);
}

private ProductWithStockResponse mapToResponse(ProductWithStockProjection projection) {
    // Convert projection to ProductWithStockResponse
    // Map category and brand if needed
}
```

### 5. Controller Layer

**WarehouseController.java - new endpoint:**

```java
@GetMapping("/{id}/products")
@PreAuthorize("hasAuthority('WAREHOUSE_READ')")
public ResponseEntity<Page<ProductWithStockResponse>> getProductsWithStock(
    @PathVariable UUID id,
    Pageable pageable
) {
    Page<ProductWithStockResponse> products = warehouseService.getProductsWithStock(id, pageable);
    return ResponseEntity.ok(products);
}
```

## Error Handling

### Error Scenarios

1. **Warehouse not found or from another tenant:**
   - Throws: `ResourceNotFoundException("Warehouse not found")`
   - Response: 404 Not Found

2. **Invalid pagination parameters:**
   - Spring validates automatically (page < 0, size < 1)
   - Response: 400 Bad Request

3. **Warehouse with no products:**
   - Returns: Empty Page with `content: []` and `totalElements: 0`
   - Response: 200 OK (valid result, not an error)

### Security Validations

- **Multi-tenancy:** Query filters by `tenantId` automatically
- **Permission:** `@PreAuthorize("hasAuthority('WAREHOUSE_READ')")`
- **Warehouse ownership:** Validates warehouse belongs to tenant before fetching products

### Expected Behaviors

- Warehouse exists but has no batches: returns empty list
- Warehouse has batches with quantity = 0: products appear with totalQuantity = 0
- Soft-deleted products: don't appear in results (`deletedAt IS NULL` filter)
- Default sorting: can use `?sort=name,asc` or `?sort=totalQuantity,desc`

## Testing Strategy

### Integration Tests

**WarehouseControllerIntegrationTest.java:**

1. **shouldReturnProductsWithStockForWarehouse**
   - Scenario: warehouse with 2 products, one with multiple batches
   - Validates: correct aggregation, pagination, correct fields

2. **shouldReturnProductWithZeroStock**
   - Scenario: product had batches but quantity = 0
   - Validates: product appears with totalQuantity = 0

3. **shouldReturnEmptyPageWhenWarehouseHasNoProducts**
   - Scenario: warehouse exists but no batches
   - Validates: empty Page, 200 OK

4. **shouldReturn404WhenWarehouseNotFound**
   - Scenario: invalid warehouseId or from another tenant
   - Validates: 404 Not Found

5. **shouldRespectPagination**
   - Scenario: warehouse with 10 products, page size = 5
   - Validates: returns 5 products, totalElements = 10

6. **shouldExcludeSoftDeletedProducts**
   - Scenario: warehouse has soft-deleted product with batches
   - Validates: deleted product doesn't appear

7. **shouldSupportSorting**
   - Scenario: products with different quantities
   - Validates: sort=totalQuantity,desc orders correctly

### Coverage

- Happy path with aggregation
- Edge cases (zero stock, empty warehouse)
- Errors (404, multi-tenancy)
- Spring features (pagination, sorting)
- Soft delete

## Files to Create/Modify

### New Files
- `src/main/java/br/com/stockshift/dto/warehouse/ProductWithStockResponse.java`
- `src/main/java/br/com/stockshift/dto/warehouse/ProductWithStockProjection.java`
- Integration tests in `WarehouseControllerIntegrationTest.java`

### Modified Files
- `src/main/java/br/com/stockshift/controller/WarehouseController.java`
- `src/main/java/br/com/stockshift/service/WarehouseService.java`
- `src/main/java/br/com/stockshift/repository/BatchRepository.java`

## Implementation Approach

**Recommended:** Query with database aggregation (Option 1)

**Rationale:**
- Most efficient for large datasets
- Native pagination works out of the box
- Database does the heavy lifting (aggregation)
- Scalable solution

**Endpoint Location:** `/api/warehouses/{id}/products` in WarehouseController
- Semantically represents "products OF a warehouse"
- Maintains REST resources organized by main entity
- Follows nested resources pattern
