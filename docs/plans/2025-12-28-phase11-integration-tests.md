# Phase 11 Integration Tests Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete integration tests for all controllers with focus on happy paths, ensuring MVP quality.

**Architecture:** Testcontainers-based integration tests using MockMvc, @WithMockUser for auth, and TenantContext for multi-tenancy. Each controller gets 2-4 critical path tests. Reusable TestDataFactory for entity creation.

**Tech Stack:** Spring Boot Test, Testcontainers, JUnit 5, MockMvc, @WithMockUser, PostgreSQL 16

---

## Task 1: Create TestDataFactory Utility

**Files:**
- Create: `src/test/java/br/com/stockshift/util/TestDataFactory.java`

**Step 1: Write the TestDataFactory class**

Create the file with all helper methods:

```java
package br.com.stockshift.util;

import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.BarcodeType;
import br.com.stockshift.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class TestDataFactory {

    public static Tenant createTenant(TenantRepository repo, String name, String document) {
        Tenant tenant = new Tenant();
        tenant.setBusinessName(name);
        tenant.setDocument(document);
        tenant.setEmail(document + "@test.com");
        tenant.setIsActive(true);
        return repo.save(tenant);
    }

    public static User createUser(UserRepository repo, PasswordEncoder encoder,
                                   UUID tenantId, String email) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPassword(encoder.encode("password123"));
        user.setFullName("Test User");
        user.setIsActive(true);
        return repo.save(user);
    }

    public static Category createCategory(CategoryRepository repo, UUID tenantId, String name) {
        Category category = new Category();
        category.setTenantId(tenantId);
        category.setName(name);
        return repo.save(category);
    }

    public static Product createProduct(ProductRepository repo, UUID tenantId,
                                        Category category, String name, String sku) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setName(name);
        product.setCategory(category);
        product.setBarcode(sku + "-BARCODE");
        product.setBarcodeType(BarcodeType.EXTERNAL);
        product.setSku(sku);
        product.setIsKit(false);
        product.setHasExpiration(false);
        product.setActive(true);
        return repo.save(product);
    }

    public static Warehouse createWarehouse(WarehouseRepository repo, UUID tenantId, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setTenantId(tenantId);
        warehouse.setName(name);
        warehouse.setAddress("Test Address 123");
        warehouse.setIsActive(true);
        return repo.save(warehouse);
    }

    public static Batch createBatch(BatchRepository repo, UUID tenantId,
                                    Product product, Warehouse warehouse, Integer quantity) {
        Batch batch = new Batch();
        batch.setTenantId(tenantId);
        batch.setProduct(product);
        batch.setWarehouse(warehouse);
        batch.setQuantity(quantity);
        batch.setUnitCost(BigDecimal.valueOf(10.00));
        batch.setExpirationDate(LocalDate.now().plusMonths(6));
        return repo.save(batch);
    }
}
```

**Step 2: Commit**

```bash
git add src/test/java/br/com/stockshift/util/TestDataFactory.java
git commit -m "test: add TestDataFactory utility for integration tests"
```

---

## Task 2: AuthenticationController Integration Tests

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/AuthenticationControllerIntegrationTest.java`

**Step 1: Write the test class structure with setup**

```java
package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.auth.LoginRequest;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.RefreshTokenRepository;
import br.com.stockshift.util.TestDataFactory;

class AuthenticationControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant testTenant;
    private User testUser;

    @BeforeEach
    void setUpTestData() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Auth Test Tenant", "11111111000101");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "auth@test.com");
    }
}
```

**Step 2: Write shouldLoginSuccessfully test**

Add to the class:

```java
    @Test
    void shouldLoginSuccessfully() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("auth@test.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.user.email").value("auth@test.com"));
    }
```

**Step 3: Run test to verify it passes**

Run: `./gradlew test --tests AuthenticationControllerIntegrationTest.shouldLoginSuccessfully`

Expected: PASS

**Step 4: Write shouldRefreshToken test**

Add to the class:

```java
    @Test
    void shouldRefreshToken() throws Exception {
        // First, login to get refresh token
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("auth@test.com");
        loginRequest.setPassword("password123");

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(loginResponse)
                .get("data").get("refreshToken").asText();

        // Now test refresh
        String refreshRequest = "{\"refreshToken\":\"" + refreshToken + "\"}";

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists());
    }
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests AuthenticationControllerIntegrationTest.shouldRefreshToken`

Expected: PASS

**Step 6: Write shouldLogoutSuccessfully test**

Add to the class:

```java
    @Test
    void shouldLogoutSuccessfully() throws Exception {
        // First, login to get refresh token
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("auth@test.com");
        loginRequest.setPassword("password123");

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(loginResponse)
                .get("data").get("refreshToken").asText();

        // Now test logout
        String logoutRequest = "{\"refreshToken\":\"" + refreshToken + "\"}";

        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(logoutRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
```

**Step 7: Run all auth tests to verify they pass**

Run: `./gradlew test --tests AuthenticationControllerIntegrationTest`

Expected: All 3 tests PASS

**Step 8: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/AuthenticationControllerIntegrationTest.java
git commit -m "test: add AuthenticationController integration tests (login, refresh, logout)"
```

---

## Task 3: CategoryController Integration Tests

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/CategoryControllerIntegrationTest.java`

**Step 1: Write the test class structure with setup**

```java
package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.product.CategoryRequest;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class CategoryControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant testTenant;
    private User testUser;

    @BeforeEach
    void setUpTestData() {
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Category Test Tenant", "22222222000102");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "category@test.com");

        TenantContext.setTenantId(testTenant.getId());
    }
}
```

**Step 2: Write shouldCreateCategory test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "category@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCreateCategory() throws Exception {
        CategoryRequest request = CategoryRequest.builder()
                .name("Electronics")
                .build();

        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Electronics"));
    }
```

**Step 3: Run test to verify it passes**

Run: `./gradlew test --tests CategoryControllerIntegrationTest.shouldCreateCategory`

Expected: PASS

**Step 4: Write shouldGetCategoryById test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "category@test.com", authorities = {"ROLE_ADMIN"})
    void shouldGetCategoryById() throws Exception {
        Category category = TestDataFactory.createCategory(categoryRepository,
                testTenant.getId(), "Books");

        mockMvc.perform(get("/api/categories/{id}", category.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(category.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Books"));
    }
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests CategoryControllerIntegrationTest.shouldGetCategoryById`

Expected: PASS

**Step 6: Write shouldListAllCategories test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "category@test.com", authorities = {"ROLE_ADMIN"})
    void shouldListAllCategories() throws Exception {
        TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Category 1");
        TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Category 2");

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
```

**Step 7: Run all category tests to verify they pass**

Run: `./gradlew test --tests CategoryControllerIntegrationTest`

Expected: All 3 tests PASS

**Step 8: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/CategoryControllerIntegrationTest.java
git commit -m "test: add CategoryController integration tests (create, getById, list)"
```

---

## Task 4: WarehouseController Integration Tests

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/WarehouseControllerIntegrationTest.java`

**Step 1: Write the test class structure with setup**

```java
package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.warehouse.WarehouseRequest;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class WarehouseControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant testTenant;
    private User testUser;

    @BeforeEach
    void setUpTestData() {
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Warehouse Test Tenant", "33333333000103");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "warehouse@test.com");

        TenantContext.setTenantId(testTenant.getId());
    }
}
```

**Step 2: Write shouldCreateWarehouse test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "warehouse@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCreateWarehouse() throws Exception {
        WarehouseRequest request = WarehouseRequest.builder()
                .name("Main Warehouse")
                .address("123 Storage St")
                .isActive(true)
                .build();

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Main Warehouse"))
                .andExpect(jsonPath("$.data.address").value("123 Storage St"));
    }
```

**Step 3: Run test to verify it passes**

Run: `./gradlew test --tests WarehouseControllerIntegrationTest.shouldCreateWarehouse`

Expected: PASS

**Step 4: Write shouldGetWarehouseById test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "warehouse@test.com", authorities = {"ROLE_ADMIN"})
    void shouldGetWarehouseById() throws Exception {
        Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository,
                testTenant.getId(), "Storage Unit A");

        mockMvc.perform(get("/api/warehouses/{id}", warehouse.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(warehouse.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Storage Unit A"));
    }
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests WarehouseControllerIntegrationTest.shouldGetWarehouseById`

Expected: PASS

**Step 6: Write shouldListAllWarehouses test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "warehouse@test.com", authorities = {"ROLE_ADMIN"})
    void shouldListAllWarehouses() throws Exception {
        TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Warehouse 1");
        TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Warehouse 2");

        mockMvc.perform(get("/api/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
```

**Step 7: Run all warehouse tests to verify they pass**

Run: `./gradlew test --tests WarehouseControllerIntegrationTest`

Expected: All 3 tests PASS

**Step 8: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/WarehouseControllerIntegrationTest.java
git commit -m "test: add WarehouseController integration tests (create, getById, list)"
```

---

## Task 5: BatchController Integration Tests

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/BatchControllerIntegrationTest.java`

**Step 1: Write the test class structure with setup**

```java
package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.warehouse.BatchRequest;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class BatchControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

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

    private Tenant testTenant;
    private User testUser;
    private Category testCategory;
    private Product testProduct;
    private Warehouse testWarehouse;

    @BeforeEach
    void setUpTestData() {
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Batch Test Tenant", "44444444000104");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "batch@test.com");

        TenantContext.setTenantId(testTenant.getId());

        testCategory = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Test Category");
        testProduct = TestDataFactory.createProduct(productRepository, testTenant.getId(),
                testCategory, "Test Product", "SKU-001");
        testWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Main Storage");
    }
}
```

**Step 2: Write shouldCreateBatch test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "batch@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCreateBatch() throws Exception {
        BatchRequest request = BatchRequest.builder()
                .productId(testProduct.getId())
                .warehouseId(testWarehouse.getId())
                .quantity(100)
                .unitCost(BigDecimal.valueOf(15.50))
                .expirationDate(LocalDate.now().plusMonths(12))
                .build();

        mockMvc.perform(post("/api/batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.unitCost").value(15.50));
    }
```

**Step 3: Run test to verify it passes**

Run: `./gradlew test --tests BatchControllerIntegrationTest.shouldCreateBatch`

Expected: PASS

**Step 4: Write shouldGetBatchById test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "batch@test.com", authorities = {"ROLE_ADMIN"})
    void shouldGetBatchById() throws Exception {
        Batch batch = TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                testProduct, testWarehouse, 50);

        mockMvc.perform(get("/api/batches/{id}", batch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(batch.getId().toString()))
                .andExpect(jsonPath("$.data.quantity").value(50));
    }
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests BatchControllerIntegrationTest.shouldGetBatchById`

Expected: PASS

**Step 6: Write shouldFindBatchesByWarehouse test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "batch@test.com", authorities = {"ROLE_ADMIN"})
    void shouldFindBatchesByWarehouse() throws Exception {
        TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                testProduct, testWarehouse, 30);
        TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                testProduct, testWarehouse, 40);

        mockMvc.perform(get("/api/batches/warehouse/{warehouseId}", testWarehouse.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
```

**Step 7: Run test to verify it passes**

Run: `./gradlew test --tests BatchControllerIntegrationTest.shouldFindBatchesByWarehouse`

Expected: PASS

**Step 8: Write shouldFindExpiringBatches test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "batch@test.com", authorities = {"ROLE_ADMIN"})
    void shouldFindExpiringBatches() throws Exception {
        // Create batch expiring in 15 days
        Batch expiringBatch = new Batch();
        expiringBatch.setTenantId(testTenant.getId());
        expiringBatch.setProduct(testProduct);
        expiringBatch.setWarehouse(testWarehouse);
        expiringBatch.setQuantity(25);
        expiringBatch.setUnitCost(BigDecimal.valueOf(10.00));
        expiringBatch.setExpirationDate(LocalDate.now().plusDays(15));
        batchRepository.save(expiringBatch);

        mockMvc.perform(get("/api/batches/expiring/{daysAhead}", 30))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
```

**Step 9: Run all batch tests to verify they pass**

Run: `./gradlew test --tests BatchControllerIntegrationTest`

Expected: All 4 tests PASS

**Step 10: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/BatchControllerIntegrationTest.java
git commit -m "test: add BatchController integration tests (create, getById, findByWarehouse, findExpiring)"
```

---

## Task 6: StockMovementController Integration Tests

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/StockMovementControllerIntegrationTest.java`

**Step 1: Write the test class structure with setup**

```java
package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.movement.StockMovementItemRequest;
import br.com.stockshift.dto.movement.StockMovementRequest;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.MovementType;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class StockMovementControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private StockMovementRepository stockMovementRepository;

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

    private Tenant testTenant;
    private User testUser;
    private Category testCategory;
    private Product testProduct;
    private Warehouse testWarehouse;
    private Batch testBatch;

    @BeforeEach
    void setUpTestData() {
        stockMovementRepository.deleteAll();
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Movement Test Tenant", "55555555000105");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "movement@test.com");

        TenantContext.setTenantId(testTenant.getId());

        testCategory = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Movement Category");
        testProduct = TestDataFactory.createProduct(productRepository, testTenant.getId(),
                testCategory, "Movement Product", "SKU-MOV-001");
        testWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Movement Warehouse");
        testBatch = TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                testProduct, testWarehouse, 100);
    }
}
```

**Step 2: Write shouldCreateStockMovement test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "movement@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCreateStockMovement() throws Exception {
        StockMovementItemRequest itemRequest = StockMovementItemRequest.builder()
                .batchId(testBatch.getId())
                .quantity(20)
                .build();

        StockMovementRequest request = StockMovementRequest.builder()
                .movementType(MovementType.PURCHASE)
                .destinationWarehouseId(testWarehouse.getId())
                .items(Collections.singletonList(itemRequest))
                .notes("Test purchase")
                .build();

        mockMvc.perform(post("/api/stock-movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.movementType").value("PURCHASE"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
```

**Step 3: Run test to verify it passes**

Run: `./gradlew test --tests StockMovementControllerIntegrationTest.shouldCreateStockMovement`

Expected: PASS

**Step 4: Write shouldExecuteStockMovement test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "movement@test.com", authorities = {"ROLE_ADMIN"})
    void shouldExecuteStockMovement() throws Exception {
        // Create movement
        StockMovementItemRequest itemRequest = StockMovementItemRequest.builder()
                .batchId(testBatch.getId())
                .quantity(15)
                .build();

        StockMovementRequest request = StockMovementRequest.builder()
                .movementType(MovementType.PURCHASE)
                .destinationWarehouseId(testWarehouse.getId())
                .items(Collections.singletonList(itemRequest))
                .build();

        String createResponse = mockMvc.perform(post("/api/stock-movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        String movementId = objectMapper.readTree(createResponse)
                .get("data").get("id").asText();

        // Execute movement
        mockMvc.perform(post("/api/stock-movements/{id}/execute", movementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // Verify stock was updated
        Batch updatedBatch = batchRepository.findById(testBatch.getId()).orElseThrow();
        assert updatedBatch.getQuantity() == 115; // 100 + 15
    }
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests StockMovementControllerIntegrationTest.shouldExecuteStockMovement`

Expected: PASS

**Step 6: Write shouldGetStockMovementById test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "movement@test.com", authorities = {"ROLE_ADMIN"})
    void shouldGetStockMovementById() throws Exception {
        StockMovementItemRequest itemRequest = StockMovementItemRequest.builder()
                .batchId(testBatch.getId())
                .quantity(10)
                .build();

        StockMovementRequest request = StockMovementRequest.builder()
                .movementType(MovementType.ADJUSTMENT)
                .destinationWarehouseId(testWarehouse.getId())
                .items(Collections.singletonList(itemRequest))
                .build();

        String createResponse = mockMvc.perform(post("/api/stock-movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        String movementId = objectMapper.readTree(createResponse)
                .get("data").get("id").asText();

        mockMvc.perform(get("/api/stock-movements/{id}", movementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(movementId))
                .andExpect(jsonPath("$.data.movementType").value("ADJUSTMENT"));
    }
```

**Step 7: Run all stock movement tests to verify they pass**

Run: `./gradlew test --tests StockMovementControllerIntegrationTest`

Expected: All 3 tests PASS

**Step 8: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/StockMovementControllerIntegrationTest.java
git commit -m "test: add StockMovementController integration tests (create, execute, getById)"
```

---

## Task 7: ReportController Integration Tests

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/ReportControllerIntegrationTest.java`

**Step 1: Write the test class structure with setup**

```java
package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class ReportControllerIntegrationTest extends BaseIntegrationTest {

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

    private Tenant testTenant;
    private User testUser;

    @BeforeEach
    void setUpTestData() {
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Report Test Tenant", "66666666000106");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "report@test.com");

        TenantContext.setTenantId(testTenant.getId());

        // Create test data for reports
        Category category = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Report Category");
        Product product = TestDataFactory.createProduct(productRepository, testTenant.getId(),
                category, "Report Product", "SKU-RPT-001");
        Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Report Warehouse");
        TestDataFactory.createBatch(batchRepository, testTenant.getId(), product, warehouse, 75);
    }
}
```

**Step 2: Write shouldGetDashboard test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "report@test.com", authorities = {"ROLE_ADMIN"})
    void shouldGetDashboard() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.totalProducts").exists())
                .andExpect(jsonPath("$.data.totalWarehouses").exists());
    }
```

**Step 3: Run test to verify it passes**

Run: `./gradlew test --tests ReportControllerIntegrationTest.shouldGetDashboard`

Expected: PASS

**Step 4: Write shouldGetStockReport test**

Add to the class:

```java
    @Test
    @WithMockUser(username = "report@test.com", authorities = {"ROLE_ADMIN"})
    void shouldGetStockReport() throws Exception {
        mockMvc.perform(get("/api/reports/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].productName").value("Report Product"))
                .andExpect(jsonPath("$.data[0].totalQuantity").value(75));
    }
```

**Step 5: Run all report tests to verify they pass**

Run: `./gradlew test --tests ReportControllerIntegrationTest`

Expected: All 2 tests PASS

**Step 6: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/ReportControllerIntegrationTest.java
git commit -m "test: add ReportController integration tests (dashboard, stockReport)"
```

---

## Task 8: Update Coverage Document

**Files:**
- Modify: `docs/testing/INTEGRATION_TESTS_COVERAGE.md`

**Step 1: Update test status in coverage document**

Update the summary table to mark completed tests:

```markdown
| Controller | Happy Paths Testados | Endpoints Não Testados | Status |
|------------|---------------------|------------------------|--------|
| Authentication | 3/3 | 0 | ✅ Completo |
| Product | 6/6 | 0 (happy paths) | ✅ Completo |
| Category | 3/9 | 6 | ✅ Completo (happy paths) |
| Warehouse | 3/7 | 4 | ✅ Completo (happy paths) |
| Batch | 4/10 | 6 | ✅ Completo (happy paths) |
| StockMovement | 3/7 | 4 | ✅ Completo (happy paths) |
| Report | 2/4 | 2 | ✅ Completo (happy paths) |
```

**Step 2: Add completion date at top of document**

Update the header:

```markdown
# Integration Tests Coverage Report

**Última Atualização:** 2025-12-28
**Fase Atual:** Phase 11 - MVP Happy Paths
**Status:** ✅ Phase 11 Completo - Todos os happy paths testados
```

**Step 3: Commit**

```bash
git add docs/testing/INTEGRATION_TESTS_COVERAGE.md
git commit -m "docs: update test coverage status - Phase 11 complete"
```

---

## Task 9: Final Verification and Summary

**Step 1: Run all integration tests**

Run: `./gradlew test`

Expected: All tests PASS with output showing:
- ProductControllerIntegrationTest: 6 tests
- AuthenticationControllerIntegrationTest: 3 tests
- CategoryControllerIntegrationTest: 3 tests
- WarehouseControllerIntegrationTest: 3 tests
- BatchControllerIntegrationTest: 4 tests
- StockMovementControllerIntegrationTest: 3 tests
- ReportControllerIntegrationTest: 2 tests

**Total:** 24 integration tests passing

**Step 2: Verify test output**

Check that:
- ✅ All 24 tests pass
- ✅ Testcontainers starts PostgreSQL successfully
- ✅ No errors in console output
- ✅ Build reports SUCCESS

**Step 3: Create summary commit**

```bash
git add -A
git commit -m "test: complete Phase 11 integration tests

Implemented happy path integration tests for all controllers:
- AuthenticationController (3 tests)
- CategoryController (3 tests)
- WarehouseController (3 tests)
- BatchController (4 tests)
- StockMovementController (3 tests)
- ReportController (2 tests)

Total: 18 new tests + 6 existing Product tests = 24 integration tests

All tests use Testcontainers PostgreSQL and @WithMockUser auth.
TestDataFactory utility created for consistent test data setup.
Coverage document updated to track Phase 2 backlog."
```

**Step 4: Verify commit history**

Run: `git log --oneline -10`

Expected to see all commits from Tasks 1-9 in order.

---

## Completion Checklist

- [ ] Task 1: TestDataFactory created and committed
- [ ] Task 2: AuthenticationController tests (3 tests) passing
- [ ] Task 3: CategoryController tests (3 tests) passing
- [ ] Task 4: WarehouseController tests (3 tests) passing
- [ ] Task 5: BatchController tests (4 tests) passing
- [ ] Task 6: StockMovementController tests (3 tests) passing
- [ ] Task 7: ReportController tests (2 tests) passing
- [ ] Task 8: Coverage document updated
- [ ] Task 9: Final verification (24 total tests passing)

---

## Success Criteria

✅ **All 24 integration tests pass**
✅ **Each controller has happy path coverage**
✅ **TestDataFactory reduces test duplication**
✅ **Coverage document tracks Phase 2 work**
✅ **All code committed with descriptive messages**
✅ **Testcontainers PostgreSQL runs cleanly**

---

## Next Steps After Plan Completion

1. **Merge to main:** Review worktree changes and merge branch
2. **Update README:** Document how to run integration tests
3. **Plan Phase 2:** Prioritize error cases and edge scenarios from coverage doc
4. **CI/CD:** Configure test pipeline to run integration tests on PRs
