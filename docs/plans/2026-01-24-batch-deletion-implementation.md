# Batch Deletion by Product and Warehouse - Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement soft-delete endpoint to delete all batches of a product within a warehouse.

**Architecture:** Add soft-delete support to Batch entity with @Where annotation for automatic filtering. Create bulk soft-delete repository method, service layer with validations, and RESTful controller endpoint. Include comprehensive integration tests.

**Tech Stack:** Spring Boot, JPA/Hibernate, PostgreSQL, JUnit 5, Spring Security

---

## Task 1: Add Soft-Delete Support to Batch Entity

**Files:**
- Modify: `src/main/java/br/com/stockshift/model/entity/Batch.java`

**Step 1: Read the current Batch entity**

Run:
```bash
cat src/main/java/br/com/stockshift/model/entity/Batch.java
```

**Step 2: Add deletedAt field and @Where annotation**

Add import:
```java
import org.hibernate.annotations.Where;
import java.time.LocalDateTime;
```

Add @Where annotation to class (before @Entity):
```java
@Where(clause = "deleted_at IS NULL")
```

Add field after version field:
```java
@Column(name = "deleted_at")
private LocalDateTime deletedAt;
```

Add getter/setter:
```java
public LocalDateTime getDeletedAt() {
    return deletedAt;
}

public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
}
```

**Step 3: Verify compilation**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/Batch.java
git commit -m "feat: add soft-delete support to Batch entity with @Where annotation

- Add deletedAt field with @Column mapping to deleted_at
- Add @Where annotation to auto-filter soft-deleted records
- All existing queries will now exclude soft-deleted batches

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Add Repository Method for Bulk Soft-Delete

**Files:**
- Modify: `src/main/java/br/com/stockshift/repository/BatchRepository.java`

**Step 1: Read the current repository**

Run:
```bash
cat src/main/java/br/com/stockshift/repository/BatchRepository.java
```

**Step 2: Add imports if needed**

Verify these imports exist, add if missing:
```java
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

**Step 3: Add soft-delete method**

Add method to interface (after existing methods):
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

**Step 4: Verify compilation**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/BatchRepository.java
git commit -m "feat: add bulk soft-delete repository method

- Add softDeleteByProductAndWarehouse method with @Modifying
- Single UPDATE query for efficiency
- Returns count of affected rows
- Prevents double-deletion with deletedAt IS NULL check

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Create BatchDeletionResponse DTO

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/warehouse/BatchDeletionResponse.java`

**Step 1: Create the DTO file**

Create file with content:
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

**Step 2: Verify compilation**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/warehouse/BatchDeletionResponse.java
git commit -m "feat: create BatchDeletionResponse DTO

- Record type with message, deletedCount, productId, warehouseId
- Used for bulk batch deletion endpoint response

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Add Service Layer Method

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/BatchService.java`

**Step 1: Read the current service**

Run:
```bash
cat src/main/java/br/com/stockshift/service/BatchService.java
```

**Step 2: Add ProductRepository dependency**

Add to constructor parameters (find the constructor with @RequiredArgsConstructor or manual constructor):
```java
private final ProductRepository productRepository;
```

Add import:
```java
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.dto.warehouse.BatchDeletionResponse;
```

**Step 3: Add deleteAllByProductAndWarehouse method**

Add method after the existing delete method:
```java
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

**Step 4: Verify compilation**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/BatchService.java
git commit -m "feat: add bulk batch deletion service method

- Add ProductRepository dependency injection
- Implement deleteAllByProductAndWarehouse with validations
- Validate warehouse and product existence before deletion
- Return BatchDeletionResponse with deletion count

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Add Controller Endpoint

**Files:**
- Modify: `src/main/java/br/com/stockshift/controller/BatchController.java`

**Step 1: Read the current controller**

Run:
```bash
cat src/main/java/br/com/stockshift/controller/BatchController.java
```

**Step 2: Add import**

Add import:
```java
import br.com.stockshift.dto.warehouse.BatchDeletionResponse;
```

**Step 3: Add endpoint method**

Add method after existing delete endpoint:
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

**Step 4: Verify compilation**

Run:
```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/BatchController.java
git commit -m "feat: add bulk batch deletion endpoint

- Add DELETE /warehouses/{id}/products/{id}/batches endpoint
- Requires BATCH_DELETE or ROLE_ADMIN permission
- Returns BatchDeletionResponse with deletion count

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Create Integration Tests - Part 1 (Setup and Success Case)

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/BatchDeletionIntegrationTest.java`

**Step 1: Create test class structure**

Create file with:
```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.warehouse.BatchDeletionResponse;
import br.com.stockshift.dto.warehouse.BatchRequest;
import br.com.stockshift.dto.warehouse.BatchResponse;
import br.com.stockshift.dto.warehouse.WarehouseRequest;
import br.com.stockshift.dto.warehouse.WarehouseResponse;
import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.dto.product.ProductResponse;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BatchDeletionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BatchRepository batchRepository;

    private UUID tenantId;
    private UUID warehouseId;
    private UUID productId;

    @BeforeEach
    void setUp() throws Exception {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        // Create warehouse
        WarehouseRequest warehouseRequest = new WarehouseRequest(
            "Test Warehouse", "São Paulo", "SP", "Test Address", true
        );
        MvcResult warehouseResult = mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(warehouseRequest)))
            .andExpect(status().isCreated())
            .andReturn();
        WarehouseResponse warehouse = objectMapper.readValue(
            warehouseResult.getResponse().getContentAsString(),
            WarehouseResponse.class
        );
        warehouseId = warehouse.id();

        // Create product
        ProductRequest productRequest = new ProductRequest(
            "Test Product", "SKU-001", "BAR-001", "Test description",
            true, 100, null, null
        );
        MvcResult productResult = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productRequest)))
            .andExpect(status().isCreated())
            .andReturn();
        ProductResponse product = objectMapper.readValue(
            productResult.getResponse().getContentAsString(),
            ProductResponse.class
        );
        productId = product.id();
    }

    @Test
    @WithMockUser(authorities = {"BATCH_DELETE"})
    void shouldDeleteAllBatchesSuccessfully() throws Exception {
        // Create 3 batches
        for (int i = 1; i <= 3; i++) {
            BatchRequest batchRequest = new BatchRequest(
                productId, warehouseId, "BATCH-" + i, 10 * i,
                null, null, null, null
            );
            mockMvc.perform(post("/api/batches")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(batchRequest)))
                .andExpect(status().isCreated());
        }

        // Delete all batches
        MvcResult result = mockMvc.perform(
                delete("/api/warehouses/{warehouseId}/products/{productId}/batches",
                    warehouseId, productId))
            .andExpect(status().isOk())
            .andReturn();

        BatchDeletionResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            BatchDeletionResponse.class
        );

        assertThat(response.deletedCount()).isEqualTo(3);
        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.warehouseId()).isEqualTo(warehouseId);
        assertThat(response.message()).contains("Successfully deleted 3 batches");

        // Verify batches are soft-deleted (have deletedAt timestamp)
        List<Batch> allBatches = batchRepository.findAll();
        assertThat(allBatches).isEmpty(); // @Where filter should exclude them

        // Verify GET endpoint doesn't return deleted batches
        mockMvc.perform(get("/api/batches/warehouse/{warehouseId}", warehouseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }
}
```

**Step 2: Run the test**

Run:
```bash
./gradlew test --tests BatchDeletionIntegrationTest.shouldDeleteAllBatchesSuccessfully
```

Expected: Test should PASS

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/BatchDeletionIntegrationTest.java
git commit -m "test: add integration test for successful batch deletion

- Test creates warehouse, product, and 3 batches
- Verifies all batches are soft-deleted
- Verifies @Where filter excludes deleted batches from queries
- Verifies deletion count in response

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 7: Create Integration Tests - Part 2 (Error Cases)

**Files:**
- Modify: `src/test/java/br/com/stockshift/controller/BatchDeletionIntegrationTest.java`

**Step 1: Add test for warehouse not found**

Add method to test class:
```java
@Test
@WithMockUser(authorities = {"BATCH_DELETE"})
void shouldReturn404WhenWarehouseNotFound() throws Exception {
    UUID nonExistentWarehouseId = UUID.randomUUID();

    mockMvc.perform(
            delete("/api/warehouses/{warehouseId}/products/{productId}/batches",
                nonExistentWarehouseId, productId))
        .andExpect(status().isNotFound());
}
```

**Step 2: Add test for product not found**

Add method to test class:
```java
@Test
@WithMockUser(authorities = {"BATCH_DELETE"})
void shouldReturn404WhenProductNotFound() throws Exception {
    UUID nonExistentProductId = UUID.randomUUID();

    mockMvc.perform(
            delete("/api/warehouses/{warehouseId}/products/{productId}/batches",
                warehouseId, nonExistentProductId))
        .andExpect(status().isNotFound());
}
```

**Step 3: Add test for no batches exist**

Add method to test class:
```java
@Test
@WithMockUser(authorities = {"BATCH_DELETE"})
void shouldReturn200WithZeroCountWhenNoBatchesExist() throws Exception {
    // Don't create any batches

    MvcResult result = mockMvc.perform(
            delete("/api/warehouses/{warehouseId}/products/{productId}/batches",
                warehouseId, productId))
        .andExpect(status().isOk())
        .andReturn();

    BatchDeletionResponse response = objectMapper.readValue(
        result.getResponse().getContentAsString(),
        BatchDeletionResponse.class
    );

    assertThat(response.deletedCount()).isEqualTo(0);
    assertThat(response.message()).contains("Successfully deleted 0 batches");
}
```

**Step 4: Add test for authorization**

Add method to test class:
```java
@Test
@WithMockUser(authorities = {"BATCH_READ"}) // Wrong permission
void shouldReturn403WhenUnauthorized() throws Exception {
    mockMvc.perform(
            delete("/api/warehouses/{warehouseId}/products/{productId}/batches",
                warehouseId, productId))
        .andExpect(status().isForbidden());
}
```

**Step 5: Run all tests**

Run:
```bash
./gradlew test --tests BatchDeletionIntegrationTest
```

Expected: All 5 tests PASS

**Step 6: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/BatchDeletionIntegrationTest.java
git commit -m "test: add error case tests for batch deletion

- Test 404 when warehouse not found
- Test 404 when product not found
- Test 200 with count=0 when no batches exist
- Test 403 when unauthorized

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 8: Create Integration Tests - Part 3 (Tenant Isolation)

**Files:**
- Modify: `src/test/java/br/com/stockshift/controller/BatchDeletionIntegrationTest.java`

**Step 1: Add test for tenant isolation**

Add method to test class:
```java
@Test
@WithMockUser(authorities = {"BATCH_DELETE"})
void shouldRespectTenantIsolation() throws Exception {
    // Create batch for current tenant
    BatchRequest batchRequest = new BatchRequest(
        productId, warehouseId, "BATCH-TENANT-1", 10,
        null, null, null, null
    );
    mockMvc.perform(post("/api/batches")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(batchRequest)))
        .andExpect(status().isCreated());

    // Switch to different tenant
    UUID differentTenantId = UUID.randomUUID();
    TenantContext.setTenantId(differentTenantId);

    // Try to delete batches as different tenant
    MvcResult result = mockMvc.perform(
            delete("/api/warehouses/{warehouseId}/products/{productId}/batches",
                warehouseId, productId))
        .andExpect(status().isNotFound()) // Warehouse doesn't exist for this tenant
        .andReturn();

    // Switch back to original tenant
    TenantContext.setTenantId(tenantId);

    // Verify batch still exists for original tenant
    mockMvc.perform(get("/api/batches/warehouse/{warehouseId}", warehouseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
}
```

**Step 2: Run the test**

Run:
```bash
./gradlew test --tests BatchDeletionIntegrationTest.shouldRespectTenantIsolation
```

Expected: Test PASS

**Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/BatchDeletionIntegrationTest.java
git commit -m "test: add tenant isolation test for batch deletion

- Test that tenant cannot delete another tenant's batches
- Verify warehouse validation respects tenant context
- Verify batches remain for original tenant

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 9: Create Integration Tests - Part 4 (Soft-Delete Verification)

**Files:**
- Modify: `src/test/java/br/com/stockshift/controller/BatchDeletionIntegrationTest.java`

**Step 1: Add test for soft-delete filter**

Add method to test class:
```java
@Test
@WithMockUser(authorities = {"BATCH_DELETE"})
void shouldFilterSoftDeletedBatchesFromAllQueries() throws Exception {
    // Create 2 batches
    BatchRequest batch1 = new BatchRequest(
        productId, warehouseId, "BATCH-FILTER-1", 10,
        null, null, null, null
    );
    BatchRequest batch2 = new BatchRequest(
        productId, warehouseId, "BATCH-FILTER-2", 20,
        null, null, null, null
    );

    mockMvc.perform(post("/api/batches")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(batch1)))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/batches")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(batch2)))
        .andExpect(status().isCreated());

    // Verify 2 batches exist before deletion
    mockMvc.perform(get("/api/batches/warehouse/{warehouseId}", warehouseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));

    // Soft delete all batches
    mockMvc.perform(
            delete("/api/warehouses/{warehouseId}/products/{productId}/batches",
                warehouseId, productId))
        .andExpect(status().isOk());

    // Verify all list endpoints don't return soft-deleted batches
    mockMvc.perform(get("/api/batches"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    mockMvc.perform(get("/api/batches/warehouse/{warehouseId}", warehouseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    mockMvc.perform(get("/api/batches/product/{productId}", productId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
}
```

**Step 2: Run the test**

Run:
```bash
./gradlew test --tests BatchDeletionIntegrationTest.shouldFilterSoftDeletedBatchesFromAllQueries
```

Expected: Test PASS

**Step 3: Run all integration tests**

Run:
```bash
./gradlew test --tests BatchDeletionIntegrationTest
```

Expected: All 7 tests PASS

**Step 4: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/BatchDeletionIntegrationTest.java
git commit -m "test: verify soft-delete filter across all endpoints

- Test that @Where annotation filters deleted batches
- Verify GET /batches excludes soft-deleted records
- Verify GET /batches/warehouse/{id} excludes soft-deleted records
- Verify GET /batches/product/{id} excludes soft-deleted records

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 10: Run Full Test Suite

**Files:**
- None (verification step)

**Step 1: Run all tests**

Run:
```bash
./gradlew test
```

Expected: All tests PASS (except the 3 pre-existing failures we noted at baseline)

**Step 2: Verify build**

Run:
```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

**Step 3: Check test report**

Run:
```bash
cat build/reports/tests/test/index.html | grep -E "tests|failures"
```

Expected: See test counts, verify BatchDeletionIntegrationTest passed

---

## Task 11: Manual Verification (Optional)

**Files:**
- None (manual testing step)

**Step 1: Start the application**

Run:
```bash
./gradlew bootRun
```

**Step 2: Test with curl (if desired)**

Create warehouse:
```bash
curl -X POST http://localhost:8080/api/warehouses \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"name":"Test WH","city":"SP","state":"SP","isActive":true}'
```

Create product:
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"name":"Test Product","sku":"SKU-001","barcode":"BAR-001","hasExpirationDate":false,"minStockLevel":10}'
```

Create batches:
```bash
curl -X POST http://localhost:8080/api/batches \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"productId":"<product-id>","warehouseId":"<warehouse-id>","quantity":50}'
```

Delete all batches:
```bash
curl -X DELETE "http://localhost:8080/api/warehouses/<warehouse-id>/products/<product-id>/batches" \
  -H "Authorization: Bearer <token>"
```

Expected: Response with deletedCount

**Step 3: Stop the application**

Press Ctrl+C

---

## Task 12: Final Review and Documentation

**Files:**
- None (review step)

**Step 1: Review all changes**

Run:
```bash
git log --oneline --graph feature/delete-batches-by-product-warehouse
```

Expected: See 8 commits for the feature

**Step 2: Verify implementation checklist from design doc**

Check:
- [x] Add `deletedAt` field to Batch entity
- [x] Add `@Where` annotation to Batch entity
- [x] Add `softDeleteByProductAndWarehouse` method to BatchRepository
- [x] Create `BatchDeletionResponse` DTO
- [x] Inject `ProductRepository` into BatchService
- [x] Add `deleteAllByProductAndWarehouse` method to BatchService
- [x] Add DELETE endpoint to BatchController
- [x] Create integration tests (7 test cases)
- [x] Verify all tests passing

**Step 3: Check for any TODO comments**

Run:
```bash
git diff main..HEAD | grep -i "TODO\|FIXME"
```

Expected: No TODOs or FIXMEs

---

## Success Criteria

✅ Entity has deletedAt field and @Where annotation
✅ Repository has bulk soft-delete method
✅ Service validates warehouse and product existence
✅ Controller endpoint requires proper authorization
✅ Integration tests cover success and error cases
✅ Soft-delete filter verified across all endpoints
✅ Tenant isolation maintained
✅ All new tests passing
✅ Build successful

## Notes for Implementation

- Follow TDD principles: write tests before or alongside implementation
- Keep commits small and focused (one logical change per commit)
- Use existing patterns from the codebase (BatchService, WarehouseService)
- All validation happens in service layer
- Controller is thin, delegates to service
- Tests use @Transactional for automatic rollback
- No database migration needed (deleted_at column exists)

## Related Skills

- @superpowers:test-driven-development - For TDD workflow
- @superpowers:verification-before-completion - Before claiming tests pass
- @superpowers:systematic-debugging - If tests fail unexpectedly
- @superpowers:finishing-a-development-branch - After implementation complete
