# Product and Batch Simultaneous Creation

**Date:** 2026-01-04
**Endpoint:** `POST /api/batches/with-product`
**Purpose:** Create a new product with initial stock in a warehouse in a single atomic operation

## Overview

This endpoint allows creating a product and its initial batch simultaneously. It's designed for scenarios where a new product is being registered for the first time and needs to be immediately added to warehouse inventory.

**Key Constraint:** This endpoint only works for NEW products. If a product with the same SKU or barcode already exists, it returns an error directing users to use the standard `POST /api/batches` endpoint instead.

## Data Structure

### Request DTO: ProductBatchRequest

Combines fields from ProductRequest and BatchRequest:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatchRequest {
    // Product fields
    @NotBlank(message = "Product name is required")
    private String name;
    private String description;
    private UUID categoryId;
    private UUID brandId;
    private String barcode;
    private BarcodeType barcodeType;
    private String sku;
    @Builder.Default
    private Boolean isKit = false;
    private Map<String, Object> attributes;
    @Builder.Default
    private Boolean hasExpiration = false;

    // Batch fields
    @NotNull(message = "Warehouse ID is required")
    private UUID warehouseId;
    @NotBlank(message = "Batch code is required")
    private String batchCode;
    @NotNull(message = "Quantity is required")
    @PositiveOrZero(message = "Quantity must be zero or positive")
    private Integer quantity;
    private LocalDate manufacturedDate;
    private LocalDate expirationDate;
    @PositiveOrZero(message = "Cost price must be zero or positive")
    private BigDecimal costPrice;
    @PositiveOrZero(message = "Selling price must be zero or positive")
    private BigDecimal sellingPrice;
}
```

### Response DTO: ProductBatchResponse

Returns both created resources:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatchResponse {
    private ProductResponse product;
    private BatchResponse batch;
}
```

## Business Logic and Validations

### Validation Flow (in order)

1. **Product Duplicity Check:**
   - If SKU provided: verify no product exists with same SKU
   - If barcode provided: verify no product exists with same barcode
   - Error response: 409 Conflict with message indicating which field is duplicate

2. **Dependencies Validation:**
   - Warehouse must exist and be active (404 if not found, 400 if inactive)
   - Category must exist if provided (404 if not found)
   - Brand must exist if provided (404 if not found)

3. **Batch Uniqueness:**
   - Verify no batch exists with same batchCode for the tenant
   - Error response: 409 Conflict

4. **Date Validations:**
   - If `hasExpiration = true`, expirationDate is required (400 if missing)
   - If both dates provided, expirationDate must be after manufacturedDate (400 if invalid)

### Atomic Operation

- Use `@Transactional` annotation
- Order of creation: Product first, then Batch
- If any step fails, entire operation rolls back
- No partial state (either both created or neither)

## Implementation Details

### BatchController

```java
@PostMapping("/with-product")
@PreAuthorize("hasAnyAuthority('BATCH_CREATE', 'PRODUCT_CREATE', 'ROLE_ADMIN')")
@Operation(summary = "Create a new product with initial stock in warehouse")
public ResponseEntity<ApiResponse<ProductBatchResponse>> createWithProduct(
        @Valid @RequestBody ProductBatchRequest request) {
    ProductBatchResponse response = batchService.createWithProduct(request);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Product and batch created successfully", response));
}
```

### BatchService Method

```java
@Transactional
public ProductBatchResponse createWithProduct(ProductBatchRequest request) {
    // 1. Validate product duplicity (SKU, barcode)
    // 2. Validate dependencies (warehouse, category, brand)
    // 3. Create product using ProductService.create()
    // 4. Create batch using existing batch creation logic
    // 5. Build and return ProductBatchResponse
}
```

### Code Reuse

- Use `ProductService.create()` for product creation
- Reuse existing batch validation logic
- Maintain consistency with existing service patterns

## Error Responses

All errors follow the existing ApiResponse pattern:

| Scenario | Status | Message |
|----------|--------|---------|
| SKU already exists | 409 | "Product with SKU '{sku}' already exists. Use POST /api/batches instead" |
| Barcode already exists | 409 | "Product with barcode '{barcode}' already exists. Use POST /api/batches instead" |
| Batch code already exists | 409 | "Batch with code '{batchCode}' already exists" |
| Warehouse not found | 404 | "Warehouse not found" |
| Warehouse inactive | 400 | "Warehouse is not active" |
| Category not found | 404 | "Category not found" |
| Brand not found | 404 | "Brand not found" |
| Missing expiration date | 400 | "Expiration date is required for products with expiration" |
| Invalid date range | 400 | "Expiration date must be after manufactured date" |

## Authorization

**Required Permissions:**
- `BATCH_CREATE` AND `PRODUCT_CREATE`, OR
- `ROLE_ADMIN`

Rationale: Operation creates both resources, so both permissions are required.

## Testing Strategy

### Integration Tests (BatchControllerTest)

1. **Success scenario:**
   - Create product + batch with all valid fields
   - Verify product exists in database
   - Verify batch exists in database
   - Verify correct relationship between them

2. **Product duplicity:**
   - Attempt to create with existing SKU → 409
   - Attempt to create with existing barcode → 409

3. **Batch duplicity:**
   - Attempt to create with existing batchCode → 409

4. **Dependencies validation:**
   - Non-existent warehouse → 404
   - Inactive warehouse → 400
   - Non-existent category → 404
   - Non-existent brand → 404

5. **Field validation:**
   - Product with hasExpiration=true without expirationDate → 400
   - expirationDate before manufacturedDate → 400
   - Missing required fields (name, warehouseId, batchCode, quantity) → 400

6. **Transaction rollback:**
   - Simulate error during batch creation after product created
   - Verify product was also rolled back (not in database)

### Unit Tests (BatchServiceTest)

- Mock ProductService and test validation logic
- Test each validation in isolation
- Verify proper error messages

## Files to Create/Modify

**New files:**
- `src/main/java/br/com/stockshift/dto/warehouse/ProductBatchRequest.java`
- `src/main/java/br/com/stockshift/dto/warehouse/ProductBatchResponse.java`

**Modified files:**
- `src/main/java/br/com/stockshift/controller/BatchController.java`
- `src/main/java/br/com/stockshift/service/BatchService.java`

**Test files:**
- `src/test/java/br/com/stockshift/controller/BatchControllerTest.java`
- `src/test/java/br/com/stockshift/service/BatchServiceTest.java`
