# Brand Management Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add brand management functionality allowing products to be associated with brands (e.g., Natura perfumes and cosmetics).

**Architecture:** Entity-first approach with TDD. Create Brand entity extending TenantAwareEntity, add Many-to-One relationship to Product, implement full CRUD with soft delete and business rule validation (prevent brand deletion when products exist).

**Tech Stack:** Spring Boot 3.x, JPA/Hibernate, PostgreSQL with UUID, Flyway migrations, Jakarta Validation, Lombok

---

## Task 1: Create Database Migration for Brands Table

**Files:**
- Create: `src/main/resources/db/migration/V11__create_brands_table.sql`

**Step 1: Write the migration SQL**

```sql
-- Brands table
CREATE TABLE brands (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    UNIQUE(tenant_id, name)
);

-- Indexes
CREATE INDEX idx_brands_tenant ON brands(tenant_id);
CREATE INDEX idx_brands_deleted_at ON brands(deleted_at) WHERE deleted_at IS NOT NULL;

-- Update trigger
CREATE TRIGGER update_brands_updated_at BEFORE UPDATE ON brands
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments
COMMENT ON TABLE brands IS 'Product brands with multi-tenancy support';
COMMENT ON COLUMN brands.name IS 'Brand name (unique per tenant)';
COMMENT ON COLUMN brands.logo_url IS 'Optional URL for brand logo';
COMMENT ON COLUMN brands.deleted_at IS 'Soft delete timestamp';
```

**Step 2: Commit migration**

```bash
git add src/main/resources/db/migration/V11__create_brands_table.sql
git commit -m "feat: add brands table migration"
```

---

## Task 2: Create Database Migration for Brand FK in Products

**Files:**
- Create: `src/main/resources/db/migration/V12__add_brand_to_products.sql`

**Step 1: Write the migration SQL**

```sql
-- Add brand_id column to products
ALTER TABLE products
    ADD COLUMN brand_id UUID REFERENCES brands(id) ON DELETE RESTRICT;

-- Index for foreign key
CREATE INDEX idx_products_brand ON products(brand_id) WHERE brand_id IS NOT NULL;

-- Comment
COMMENT ON COLUMN products.brand_id IS 'Optional brand association';
```

**Step 2: Commit migration**

```bash
git add src/main/resources/db/migration/V12__add_brand_to_products.sql
git commit -m "feat: add brand_id column to products table"
```

---

## Task 3: Create Brand Entity

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/Brand.java`

**Step 1: Write the Brand entity**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "brands", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "tenant_id", "name" })
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Brand extends TenantAwareEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
```

**Step 2: Commit entity**

```bash
git add src/main/java/br/com/stockshift/model/entity/Brand.java
git commit -m "feat: add Brand entity"
```

---

## Task 4: Add Brand Relationship to Product Entity

**Files:**
- Modify: `src/main/java/br/com/stockshift/model/entity/Product.java:31-32`

**Step 1: Add brand field to Product**

Add after the `category` field (around line 31):

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "brand_id")
private Brand brand;
```

**Step 2: Commit change**

```bash
git add src/main/java/br/com/stockshift/model/entity/Product.java
git commit -m "feat: add brand relationship to Product entity"
```

---

## Task 5: Create BrandRepository

**Files:**
- Create: `src/main/java/br/com/stockshift/repository/BrandRepository.java`

**Step 1: Write the repository interface**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BrandRepository extends JpaRepository<Brand, UUID> {

    Optional<Brand> findByTenantIdAndId(UUID tenantId, UUID id);

    List<Brand> findAllByTenantIdAndDeletedAtIsNull(UUID tenantId);

    boolean existsByNameAndTenantIdAndDeletedAtIsNull(String name, UUID tenantId);

    boolean existsByNameAndTenantIdAndDeletedAtIsNullAndIdNot(String name, UUID tenantId, UUID id);
}
```

**Step 2: Commit repository**

```bash
git add src/main/java/br/com/stockshift/repository/BrandRepository.java
git commit -m "feat: add BrandRepository"
```

---

## Task 6: Add Brand Check Method to ProductRepository

**Files:**
- Modify: `src/main/java/br/com/stockshift/repository/ProductRepository.java`

**Step 1: Add method to check products by brand**

Add this method to the ProductRepository interface:

```java
boolean existsByBrandIdAndDeletedAtIsNull(UUID brandId);
```

**Step 2: Commit change**

```bash
git add src/main/java/br/com/stockshift/repository/ProductRepository.java
git commit -m "feat: add brand existence check to ProductRepository"
```

---

## Task 7: Create Brand DTOs

**Files:**
- Create: `src/main/java/br/com/stockshift/dto/brand/BrandRequest.java`
- Create: `src/main/java/br/com/stockshift/dto/brand/BrandResponse.java`

**Step 1: Create BrandRequest DTO**

```java
package br.com.stockshift.dto.brand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrandRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 255, message = "Nome não pode exceder 255 caracteres")
    private String name;

    @Size(max = 500, message = "URL do logo não pode exceder 500 caracteres")
    private String logoUrl;
}
```

**Step 2: Create BrandResponse DTO**

```java
package br.com.stockshift.dto.brand;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandResponse {

    private UUID id;
    private String name;
    private String logoUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Step 3: Commit DTOs**

```bash
git add src/main/java/br/com/stockshift/dto/brand/
git commit -m "feat: add Brand DTOs (Request and Response)"
```

---

## Task 8: Update Product DTOs with Brand

**Files:**
- Modify: `src/main/java/br/com/stockshift/dto/product/ProductRequest.java`
- Modify: `src/main/java/br/com/stockshift/dto/product/ProductResponse.java`

**Step 1: Read current ProductRequest**

Run: `cat src/main/java/br/com/stockshift/dto/product/ProductRequest.java`

**Step 2: Add brandId field to ProductRequest**

Add this field to the class:

```java
private UUID brandId;
```

**Step 3: Read current ProductResponse**

Run: `cat src/main/java/br/com/stockshift/dto/product/ProductResponse.java`

**Step 4: Add brand field to ProductResponse**

Add this import:
```java
import br.com.stockshift.dto.brand.BrandResponse;
```

Add this field to the class:
```java
private BrandResponse brand;
```

**Step 5: Commit changes**

```bash
git add src/main/java/br/com/stockshift/dto/product/ProductRequest.java src/main/java/br/com/stockshift/dto/product/ProductResponse.java
git commit -m "feat: add brand fields to Product DTOs"
```

---

## Task 9: Create BrandService

**Files:**
- Create: `src/main/java/br/com/stockshift/service/BrandService.java`

**Step 1: Write BrandService implementation**

```java
package br.com.stockshift.service;

import br.com.stockshift.dto.brand.BrandRequest;
import br.com.stockshift.dto.brand.BrandResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Brand;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Transactional
    public BrandResponse create(BrandRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Check if name already exists for this tenant
        if (brandRepository.existsByNameAndTenantIdAndDeletedAtIsNull(request.getName(), tenantId)) {
            throw new BusinessException("Já existe uma marca com este nome");
        }

        Brand brand = new Brand();
        brand.setTenantId(tenantId);
        brand.setName(request.getName());
        brand.setLogoUrl(request.getLogoUrl());

        Brand saved = brandRepository.save(brand);
        log.info("Created brand {} for tenant {}", saved.getId(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return brandRepository.findAllByTenantIdAndDeletedAtIsNull(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BrandResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Brand brand = findBrandByIdAndTenant(id, tenantId);
        return mapToResponse(brand);
    }

    @Transactional
    public BrandResponse update(UUID id, BrandRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        Brand brand = findBrandByIdAndTenant(id, tenantId);

        // Check if new name conflicts with another brand
        if (brandRepository.existsByNameAndTenantIdAndDeletedAtIsNullAndIdNot(
                request.getName(), tenantId, id)) {
            throw new BusinessException("Já existe outra marca com este nome");
        }

        brand.setName(request.getName());
        brand.setLogoUrl(request.getLogoUrl());

        Brand updated = brandRepository.save(brand);
        log.info("Updated brand {} for tenant {}", id, tenantId);

        return mapToResponse(updated);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Brand brand = findBrandByIdAndTenant(id, tenantId);

        // Check if any products are using this brand
        if (productRepository.existsByBrandIdAndDeletedAtIsNull(brand.getId())) {
            throw new BusinessException(
                "Não é possível deletar marca com produtos vinculados");
        }

        brand.setDeletedAt(LocalDateTime.now());
        brandRepository.save(brand);
        log.info("Soft deleted brand {} for tenant {}", id, tenantId);
    }

    private Brand findBrandByIdAndTenant(UUID id, UUID tenantId) {
        return brandRepository.findByTenantIdAndId(tenantId, id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", "id", id));
    }

    private BrandResponse mapToResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .logoUrl(brand.getLogoUrl())
                .createdAt(brand.getCreatedAt())
                .updatedAt(brand.getUpdatedAt())
                .build();
    }
}
```

**Step 2: Commit service**

```bash
git add src/main/java/br/com/stockshift/service/BrandService.java
git commit -m "feat: add BrandService with CRUD operations"
```

---

## Task 10: Update ProductService to Handle Brand

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/ProductService.java`

**Step 1: Read current ProductService**

Run: `cat src/main/java/br/com/stockshift/service/ProductService.java`

**Step 2: Add BrandRepository injection**

Add to constructor dependencies:
```java
private final BrandRepository brandRepository;
```

Add import:
```java
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.model.entity.Brand;
```

**Step 3: Update create method to handle brand**

In the `create` method, after handling category, add:

```java
// Validate and set brand if provided
if (request.getBrandId() != null) {
    Brand brand = brandRepository.findByTenantIdAndId(tenantId, request.getBrandId())
            .filter(b -> b.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException("Brand", "id", request.getBrandId()));
    product.setBrand(brand);
}
```

**Step 4: Update update method to handle brand**

In the `update` method, after handling category, add:

```java
// Validate and set brand if provided
if (request.getBrandId() != null) {
    Brand brand = brandRepository.findByTenantIdAndId(tenantId, request.getBrandId())
            .filter(b -> b.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException("Brand", "id", request.getBrandId()));
    product.setBrand(brand);
} else {
    product.setBrand(null);
}
```

**Step 5: Update mapToResponse to include brand**

In the `mapToResponse` method, add brand mapping. Find where CategoryResponse is created and add similar code for BrandResponse:

```java
.brand(product.getBrand() != null ? mapBrandToResponse(product.getBrand()) : null)
```

Add this helper method:
```java
import br.com.stockshift.dto.brand.BrandResponse;

private BrandResponse mapBrandToResponse(Brand brand) {
    return BrandResponse.builder()
            .id(brand.getId())
            .name(brand.getName())
            .logoUrl(brand.getLogoUrl())
            .createdAt(brand.getCreatedAt())
            .updatedAt(brand.getUpdatedAt())
            .build();
}
```

**Step 6: Commit changes**

```bash
git add src/main/java/br/com/stockshift/service/ProductService.java
git commit -m "feat: integrate brand handling in ProductService"
```

---

## Task 11: Create BrandController

**Files:**
- Create: `src/main/java/br/com/stockshift/controller/BrandController.java`

**Step 1: Write BrandController**

```java
package br.com.stockshift.controller;

import br.com.stockshift.dto.brand.BrandRequest;
import br.com.stockshift.dto.brand.BrandResponse;
import br.com.stockshift.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @PostMapping
    public ResponseEntity<BrandResponse> create(@Valid @RequestBody BrandRequest request) {
        BrandResponse response = brandService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<BrandResponse>> findAll() {
        List<BrandResponse> brands = brandService.findAll();
        return ResponseEntity.ok(brands);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BrandResponse> findById(@PathVariable UUID id) {
        BrandResponse response = brandService.findById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BrandResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody BrandRequest request) {
        BrandResponse response = brandService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        brandService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

**Step 2: Commit controller**

```bash
git add src/main/java/br/com/stockshift/controller/BrandController.java
git commit -m "feat: add BrandController with REST endpoints"
```

---

## Task 12: Write BrandController Integration Tests

**Files:**
- Create: `src/test/java/br/com/stockshift/controller/BrandControllerIntegrationTest.java`

**Step 1: Read BaseIntegrationTest to understand test pattern**

Run: `cat src/test/java/br/com/stockshift/BaseIntegrationTest.java`

**Step 2: Write comprehensive integration tests**

```java
package br.com.stockshift.controller;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.brand.BrandRequest;
import br.com.stockshift.dto.brand.BrandResponse;
import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.model.entity.Brand;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BrandControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BrandRepository brandRepository;

    @Test
    void shouldCreateBrand() throws Exception {
        BrandRequest request = new BrandRequest("Natura", "https://example.com/natura-logo.png");

        mockMvc.perform(post("/api/brands")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Natura"))
                .andExpect(jsonPath("$.logoUrl").value("https://example.com/natura-logo.png"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void shouldNotCreateBrandWithDuplicateName() throws Exception {
        // Create first brand
        Brand existingBrand = TestDataFactory.createBrand(tenant, "Natura");
        brandRepository.save(existingBrand);

        // Try to create duplicate
        BrandRequest request = new BrandRequest("Natura", null);

        mockMvc.perform(post("/api/brands")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Já existe uma marca com este nome"));
    }

    @Test
    void shouldListAllBrands() throws Exception {
        Brand brand1 = TestDataFactory.createBrand(tenant, "Natura");
        Brand brand2 = TestDataFactory.createBrand(tenant, "Boticário");
        brandRepository.save(brand1);
        brandRepository.save(brand2);

        mockMvc.perform(get("/api/brands")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Natura", "Boticário")));
    }

    @Test
    void shouldGetBrandById() throws Exception {
        Brand brand = TestDataFactory.createBrand(tenant, "Natura");
        brand = brandRepository.save(brand);

        mockMvc.perform(get("/api/brands/" + brand.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(brand.getId().toString()))
                .andExpect(jsonPath("$.name").value("Natura"));
    }

    @Test
    void shouldReturnNotFoundForNonExistentBrand() throws Exception {
        mockMvc.perform(get("/api/brands/" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateBrand() throws Exception {
        Brand brand = TestDataFactory.createBrand(tenant, "Natura");
        brand = brandRepository.save(brand);

        BrandRequest updateRequest = new BrandRequest("Natura Updated", "https://new-logo.png");

        mockMvc.perform(put("/api/brands/" + brand.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Natura Updated"))
                .andExpect(jsonPath("$.logoUrl").value("https://new-logo.png"));
    }

    @Test
    void shouldNotUpdateBrandWithDuplicateName() throws Exception {
        Brand brand1 = TestDataFactory.createBrand(tenant, "Natura");
        Brand brand2 = TestDataFactory.createBrand(tenant, "Boticário");
        brandRepository.save(brand1);
        brandRepository.save(brand2);

        BrandRequest updateRequest = new BrandRequest("Boticário", null);

        mockMvc.perform(put("/api/brands/" + brand1.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Já existe outra marca com este nome"));
    }

    @Test
    void shouldDeleteBrandWithoutProducts() throws Exception {
        Brand brand = TestDataFactory.createBrand(tenant, "Natura");
        brand = brandRepository.save(brand);

        mockMvc.perform(delete("/api/brands/" + brand.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify soft delete
        Brand deleted = brandRepository.findById(brand.getId()).orElseThrow();
        assert deleted.getDeletedAt() != null;
    }

    @Test
    void shouldNotDeleteBrandWithProducts() throws Exception {
        Brand brand = TestDataFactory.createBrand(tenant, "Natura");
        brand = brandRepository.save(brand);

        // Create product with this brand
        ProductRequest productRequest = TestDataFactory.createProductRequest();
        productRequest.setBrandId(brand.getId());

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated());

        // Try to delete brand
        mockMvc.perform(delete("/api/brands/" + brand.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Não é possível deletar marca com produtos vinculados"));
    }
}
```

**Step 3: Add factory method to TestDataFactory**

Run: `cat src/test/java/br/com/stockshift/util/TestDataFactory.java` and add:

```java
import br.com.stockshift.model.entity.Brand;

public static Brand createBrand(Tenant tenant, String name) {
    Brand brand = new Brand();
    brand.setTenantId(tenant.getId());
    brand.setName(name);
    return brand;
}
```

**Step 4: Run tests**

Run: `./gradlew test --tests BrandControllerIntegrationTest`
Expected: All tests pass

**Step 5: Commit tests**

```bash
git add src/test/java/br/com/stockshift/controller/BrandControllerIntegrationTest.java src/test/java/br/com/stockshift/util/TestDataFactory.java
git commit -m "test: add BrandController integration tests"
```

---

## Task 13: Update ProductController Integration Tests

**Files:**
- Modify: `src/test/java/br/com/stockshift/controller/ProductControllerIntegrationTest.java`

**Step 1: Add test for creating product with brand**

Add this test method:

```java
@Test
void shouldCreateProductWithBrand() throws Exception {
    Brand brand = TestDataFactory.createBrand(tenant, "Natura");
    brand = brandRepository.save(brand);

    ProductRequest request = TestDataFactory.createProductRequest();
    request.setBrandId(brand.getId());

    mockMvc.perform(post("/api/products")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.brand").exists())
            .andExpect(jsonPath("$.brand.id").value(brand.getId().toString()))
            .andExpect(jsonPath("$.brand.name").value("Natura"));
}
```

**Step 2: Add test for creating product without brand**

```java
@Test
void shouldCreateProductWithoutBrand() throws Exception {
    ProductRequest request = TestDataFactory.createProductRequest();
    request.setBrandId(null);

    mockMvc.perform(post("/api/products")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.brand").isEmpty());
}
```

**Step 3: Add test for invalid brand ID**

```java
@Test
void shouldNotCreateProductWithInvalidBrand() throws Exception {
    ProductRequest request = TestDataFactory.createProductRequest();
    request.setBrandId(UUID.randomUUID());

    mockMvc.perform(post("/api/products")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value(containsString("Brand")));
}
```

**Step 4: Add import for BrandRepository**

Add to class fields:
```java
@Autowired
private BrandRepository brandRepository;
```

**Step 5: Run tests**

Run: `./gradlew test --tests ProductControllerIntegrationTest`
Expected: All tests pass

**Step 6: Commit changes**

```bash
git add src/test/java/br/com/stockshift/controller/ProductControllerIntegrationTest.java
git commit -m "test: add brand integration tests to ProductController"
```

---

## Task 14: Run Full Test Suite

**Step 1: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 2: Build project**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 3: If all pass, commit final verification**

```bash
git commit --allow-empty -m "chore: verify all tests passing for brand management feature"
```

---

## Task 15: Final Review and Documentation

**Step 1: Check all files were created/modified**

Run: `git log --oneline -15`
Expected: See all commits from this plan

**Step 2: Verify migrations**

Run: `ls -la src/main/resources/db/migration/V1*.sql`
Expected: See V11 and V12 migrations

**Step 3: Run application (if database available)**

Run: `./gradlew bootRun`
Expected: Application starts without errors, migrations apply successfully

**Step 4: Manual API testing (optional)**

Test the brand endpoints manually:
- POST /api/brands - Create brand
- GET /api/brands - List brands
- GET /api/brands/{id} - Get brand
- PUT /api/brands/{id} - Update brand
- DELETE /api/brands/{id} - Delete brand
- POST /api/products with brandId - Create product with brand

---

## Completion Checklist

- [ ] V11 migration created (brands table)
- [ ] V12 migration created (brand_id in products)
- [ ] Brand entity created
- [ ] Product entity updated with brand relationship
- [ ] BrandRepository created
- [ ] ProductRepository updated with brand check
- [ ] Brand DTOs created (Request/Response)
- [ ] Product DTOs updated with brand fields
- [ ] BrandService implemented with full CRUD
- [ ] ProductService updated to handle brands
- [ ] BrandController created
- [ ] BrandController integration tests pass
- [ ] ProductController tests updated and pass
- [ ] Full test suite passes
- [ ] Build successful

## Success Criteria

✅ All migrations apply cleanly
✅ All tests pass (unit + integration)
✅ Build successful
✅ Brand CRUD operations working
✅ Product-brand relationship working
✅ Business rules enforced (unique name, no delete with products)
✅ Soft delete implemented correctly
✅ Multi-tenancy respected

---

**Implementation Notes:**

- Follow TDD where possible (tests before implementation)
- Commit after each task (frequent small commits)
- Use existing patterns (Category is best reference)
- All business logic in Service layer
- Controller only handles HTTP concerns
- Validate at both DTO (Jakarta) and Service (business rules) layers
- Always filter deleted records (deletedAt IS NULL)
- Always check tenant isolation
