# Bulk Update Batch Selling Price — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `PATCH` endpoint to update `sellingPrice` on all batches of a product in a specific warehouse.

**Architecture:** Bulk JPQL update via `@Modifying` query in `BatchRepository`, service method with warehouse-access validation and summary audit recording, exposed through a new controller endpoint. Follows the existing `deleteAllByProductAndWarehouse` pattern.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Data JPA, JUnit 5 + MockMvc

## Global Constraints

- No database migration (column `selling_price` already exists on `batches`)
- Reuse existing permission `batches:update`
- No domain model changes
- Summary audit only (one record with `affectedCount`), not per-batch auditing

---

### Task 1: Create DTOs — Request and Response

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/warehouse/BatchSellingPriceUpdateRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/warehouse/BatchSellingPriceUpdateResponse.java`

**Interfaces:**
- Produces: `BatchSellingPriceUpdateRequest(Long sellingPrice)` — used by Task 4 controller
- Produces: `BatchSellingPriceUpdateResponse(String message, int affectedCount, UUID productId, UUID warehouseId)` — used by Task 3 service and Task 4 controller

- [ ] **Step 1: Write `BatchSellingPriceUpdateRequest.java`**

```java
package br.com.stockshift.dto.warehouse;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchSellingPriceUpdateRequest {

    @NotNull(message = "Selling price is required")
    @PositiveOrZero(message = "Selling price must be zero or positive")
    @Schema(description = "Selling price in cents", example = "1575")
    private Long sellingPrice;
}
```

- [ ] **Step 2: Write `BatchSellingPriceUpdateResponse.java`**

```java
package br.com.stockshift.dto.warehouse;

import java.util.UUID;

public record BatchSellingPriceUpdateResponse(
    String message,
    int affectedCount,
    UUID productId,
    UUID warehouseId
) {}
```

- [ ] **Step 3: Compile to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/warehouse/BatchSellingPriceUpdateRequest.java \
        src/main/java/br/com/stockshift/dto/warehouse/BatchSellingPriceUpdateResponse.java
git commit -m "feat: add BatchSellingPriceUpdate DTOs

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Add bulk update query to BatchRepository

**Files:**
- Modify: `src/main/java/br/com/stockshift/repository/BatchRepository.java` — add new method

**Interfaces:**
- Consumes: (none from earlier tasks)
- Produces: `int updateSellingPriceByProductAndWarehouse(UUID productId, UUID warehouseId, UUID tenantId, Long sellingPrice)` — used by Task 3 service

- [ ] **Step 1: Add the query method**

Open `src/main/java/br/com/stockshift/repository/BatchRepository.java` and add after the `softDeleteByProduct` method (line 133):

```java
@Modifying
@Query("UPDATE Batch b SET b.sellingPrice = :sellingPrice " +
    "WHERE b.product.id = :productId " +
    "AND b.warehouse.id = :warehouseId " +
    "AND b.tenantId = :tenantId " +
    "AND b.deletedAt IS NULL")
int updateSellingPriceByProductAndWarehouse(
    @Param("productId") UUID productId,
    @Param("warehouseId") UUID warehouseId,
    @Param("tenantId") UUID tenantId,
    @Param("sellingPrice") Long sellingPrice);
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/repository/BatchRepository.java
git commit -m "feat: add bulk update selling price query to BatchRepository

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Add service method

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/BatchService.java` — add new method

**Interfaces:**
- Consumes: `int updateSellingPriceByProductAndWarehouse(...)` from Task 2
- Consumes: `BatchSellingPriceUpdateResponse(...)` record from Task 1
- Produces: `BatchSellingPriceUpdateResponse updateSellingPriceByProductAndWarehouse(UUID warehouseId, UUID productId, Long sellingPrice)` — used by Task 4 controller

- [ ] **Step 1: Add the service method**

Open `src/main/java/br/com/stockshift/service/BatchService.java` and add the new method before `private BatchResponse mapToResponse` (before line 432). Also add the import for the new response record:

Add import at top (after `import br.com.stockshift.dto.warehouse.BatchDeletionResponse;`):
```java
import br.com.stockshift.dto.warehouse.BatchSellingPriceUpdateResponse;
```

Add the method:
```java
@Transactional
public BatchSellingPriceUpdateResponse updateSellingPriceByProductAndWarehouse(
    UUID warehouseId,
    UUID productId,
    Long sellingPrice
) {
    UUID tenantId = TenantContext.getTenantId();
    warehouseAccessService.validateWarehouseAccess(warehouseId);

    // Validate warehouse exists and belongs to tenant
    warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Warehouse", "id", warehouseId));

    // Validate product exists and belongs to tenant
    productRepository.findByTenantIdAndId(tenantId, productId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Product", "id", productId));

    int affectedCount = batchRepository.updateSellingPriceByProductAndWarehouse(
        productId, warehouseId, tenantId, sellingPrice);

    auditService.record(AuditEventCreateRequest.builder()
        .operation(AuditService.OPERATION_TECHNICAL)
        .action("BATCHES_SELLING_PRICE_UPDATED")
        .outcome(AuditService.OUTCOME_SUCCESS)
        .resourceType("BATCH")
        .resourceId(productId + ":" + warehouseId)
        .metadata(Map.of(
            "affectedCount", affectedCount,
            "newSellingPrice", sellingPrice,
            "productId", productId.toString(),
            "warehouseId", warehouseId.toString()))
        .build());

    log.info("Updated selling price to {} for {} batches of product {} in warehouse {} for tenant {}",
        sellingPrice, affectedCount, productId, warehouseId, tenantId);

    return new BatchSellingPriceUpdateResponse(
        "Selling price updated for " + affectedCount + " batches",
        affectedCount,
        productId,
        warehouseId);
}
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/service/BatchService.java
git commit -m "feat: add bulk update selling price method to BatchService

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Add controller endpoint

**Files:**
- Modify: `src/main/java/br/com/stockshift/controller/BatchController.java` — add new endpoint

**Interfaces:**
- Consumes: `BatchSellingPriceUpdateRequest` from Task 1
- Consumes: `BatchSellingPriceUpdateResponse` from Task 1
- Consumes: `BatchService.updateSellingPriceByProductAndWarehouse(...)` from Task 3

- [ ] **Step 1: Add the endpoint method**

Open `src/main/java/br/com/stockshift/controller/BatchController.java` and add the new endpoint before the closing brace of the class (before line 147). Also add the imports:

Add imports:
```java
import br.com.stockshift.dto.warehouse.BatchSellingPriceUpdateRequest;
import br.com.stockshift.dto.warehouse.BatchSellingPriceUpdateResponse;
```

Add method (place right after the `deleteAllBatchesByProductAndWarehouse` method at line 146):
```java
@PatchMapping("/warehouses/{warehouseId}/products/{productId}/batches/selling-price")
@PreAuthorize("@permissionGuard.has('batches:update') and @warehouseGuard.isCurrent(#warehouseId)")
@Operation(summary = "Update selling price for all batches of a product in a warehouse")
public ResponseEntity<ApiResponse<BatchSellingPriceUpdateResponse>> updateSellingPrice(
    @PathVariable UUID warehouseId,
    @PathVariable UUID productId,
    @Valid @RequestBody BatchSellingPriceUpdateRequest request
) {
    BatchSellingPriceUpdateResponse response = batchService
        .updateSellingPriceByProductAndWarehouse(warehouseId, productId, request.getSellingPrice());
    return ResponseEntity.ok(ApiResponse.success(response.message(), response));
}
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/BatchController.java
git commit -m "feat: add PATCH endpoint for bulk selling price update

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Write integration tests

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/BatchSellingPriceUpdateIntegrationTest.java`

**Interfaces:**
- Consumes: All implementation from Tasks 1–4
- Consumes: `BaseIntegrationTest`, `TestDataFactory`, `BatchRepository`, etc. (existing test infrastructure)

- [ ] **Step 1: Write the test class**

```java
package br.com.stockshift.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.warehouse.BatchSellingPriceUpdateRequest;
import br.com.stockshift.dto.warehouse.BatchSellingPriceUpdateResponse;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional
class BatchSellingPriceUpdateIntegrationTest extends BaseIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID tenantId;
    private UUID warehouseId;
    private UUID productId;
    private Warehouse testWarehouse;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant testTenant = TestDataFactory.createTenant(
            tenantRepository,
            "Selling Price Update Test Tenant",
            "66666666000106");
        tenantId = testTenant.getId();

        TestDataFactory.createUser(
            userRepository,
            passwordEncoder,
            tenantId,
            "sellingpriceupdate@test.com");

        TenantContext.setTenantId(tenantId);

        Category testCategory = TestDataFactory.createCategory(
            categoryRepository,
            tenantId,
            "Selling Price Update Category");

        testWarehouse = TestDataFactory.createWarehouse(
            warehouseRepository,
            tenantId,
            "Test Warehouse");
        warehouseId = testWarehouse.getId();

        testProduct = TestDataFactory.createProduct(
            productRepository,
            tenantId,
            testCategory,
            "Test Product",
            "SKU-SPU-001");
        productId = testProduct.getId();
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN" })
    void shouldUpdateSellingPriceForAllBatchesOfProductInWarehouse() throws Exception {
        // Create 3 batches with selling price 1500 (default)
        TestDataFactory.createBatch(batchRepository, tenantId, testProduct, testWarehouse, "BATCH-A", 10);
        TestDataFactory.createBatch(batchRepository, tenantId, testProduct, testWarehouse, "BATCH-B", 20);
        TestDataFactory.createBatch(batchRepository, tenantId, testProduct, testWarehouse, "BATCH-C", 30);

        Long newSellingPrice = 2000L;
        BatchSellingPriceUpdateRequest request = new BatchSellingPriceUpdateRequest(newSellingPrice);

        String responseContent = mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.affectedCount").value(3))
            .andExpect(jsonPath("$.data.productId").value(productId.toString()))
            .andExpect(jsonPath("$.data.warehouseId").value(warehouseId.toString()))
            .andReturn().getResponse().getContentAsString();

        BatchSellingPriceUpdateResponse response = objectMapper.readValue(
            responseContent,
            objectMapper.getTypeFactory().constructParametricType(
                br.com.stockshift.dto.ApiResponse.class,
                BatchSellingPriceUpdateResponse.class));

        assertThat(response.getData().affectedCount()).isEqualTo(3);
        assertThat(response.getData().message()).contains("Selling price updated for 3 batches");

        // Verify each batch was updated
        List<Batch> batches = batchRepository.findByProductIdAndWarehouseIdAndTenantId(
            productId, warehouseId, tenantId);
        assertThat(batches).hasSize(3);
        assertThat(batches).allMatch(b -> b.getSellingPrice().equals(newSellingPrice));
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN" })
    void shouldReturn200WithZeroCountWhenNoBatchesExist() throws Exception {
        // Don't create any batches
        Long newSellingPrice = 2000L;
        BatchSellingPriceUpdateRequest request = new BatchSellingPriceUpdateRequest(newSellingPrice);

        String responseContent = mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        BatchSellingPriceUpdateResponse response = objectMapper.readValue(
            responseContent,
            objectMapper.getTypeFactory().constructParametricType(
                br.com.stockshift.dto.ApiResponse.class,
                BatchSellingPriceUpdateResponse.class));

        assertThat(response.getData().affectedCount()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN" })
    void shouldReturn404WhenWarehouseNotFound() throws Exception {
        UUID nonExistentWarehouseId = UUID.randomUUID();
        BatchSellingPriceUpdateRequest request = new BatchSellingPriceUpdateRequest(2000L);

        mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                nonExistentWarehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN" })
    void shouldReturn404WhenProductNotFound() throws Exception {
        UUID nonExistentProductId = UUID.randomUUID();
        BatchSellingPriceUpdateRequest request = new BatchSellingPriceUpdateRequest(2000L);

        mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, nonExistentProductId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = { "BATCH_READ" })
    void shouldReturn403WhenInsufficientPermission() throws Exception {
        BatchSellingPriceUpdateRequest request = new BatchSellingPriceUpdateRequest(2000L);

        mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN" })
    void shouldReturn400WhenSellingPriceIsNull() throws Exception {
        String body = "{}";

        mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN" })
    void shouldReturn400WhenSellingPriceIsNegative() throws Exception {
        String body = "{\"sellingPrice\": -100}";

        mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN", "BATCH_CREATE", "BATCH_UPDATE", "BATCH_READ" })
    void shouldRespectTenantIsolation() throws Exception {
        // Create a batch for the current tenant
        TestDataFactory.createBatch(batchRepository, tenantId, testProduct, testWarehouse, "BATCH-ISO", 10);

        // Switch to a different tenant
        UUID differentTenantId = UUID.randomUUID();
        TenantContext.setTenantId(differentTenantId);

        BatchSellingPriceUpdateRequest request = new BatchSellingPriceUpdateRequest(9999L);
        // The warehouse does not exist for the different tenant → 404
        mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());

        // Switch back to original tenant
        TenantContext.setTenantId(tenantId);

        // Verify the batch still has the original selling price (not updated)
        List<Batch> batches = batchRepository.findByProductIdAndWarehouseIdAndTenantId(
            productId, warehouseId, tenantId);
        assertThat(batches).hasSize(1);
        assertThat(batches.get(0).getSellingPrice()).isEqualTo(1500L);
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew test --tests "br.com.stockshift.controller.BatchSellingPriceUpdateIntegrationTest"`
Expected: All 8 tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/BatchSellingPriceUpdateIntegrationTest.java
git commit -m "test: add integration tests for bulk selling price update

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Run full test suite

- [ ] **Step 1: Run all tests to ensure no regressions**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Commit (if any formatting changes)**

```bash
git add . && git diff --cached --quiet || git commit -m "chore: finalize bulk selling price update

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

**Total tasks:** 6  
**New files:** 3 (2 DTOs + 1 test)  
**Modified files:** 3 (repository, service, controller)  
**Estimated new lines:** ~180 (70 implementation + 110 test)
