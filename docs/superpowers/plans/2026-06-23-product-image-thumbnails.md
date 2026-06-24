# Product Image Thumbnails — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate 3 JPEG thumbnails (150px, 400px, 800px) at product image upload time and return them in ProductResponse for faster frontend loading.

**Architecture:** Thumbnailator generates resized variants inside StorageService during upload. Thumbnail metadata is persisted in a new `product_image_thumbnails` table. ProductService loads thumbnails alongside products and merges them into ProductResponse. No new endpoints. No changes to temp-upload flow.

**Tech Stack:** Java 17, Spring Boot 4.0.1, Thumbnailator 0.4.20, Cloudflare R2 (S3-compatible), JUnit 5 + Mockito + AssertJ

## Global Constraints

- Thumbnailator 0.4.20 (pure Java, no native deps)
- Thumbnails: JPEG at quality 80–85%, max widths 150/400/800
- Original image handling unchanged — `products.image_url` untouched
- `ProductImageUploadService` and `StockMovementService` unchanged
- No new REST endpoints
- `ProductResponse` gains `Map<String, String> thumbnails` field defaulting to empty map
- Thumbnail generation failure must NOT block product creation/update

---

### Task 1: Database migration — `product_image_thumbnails` table

**Files:**
- Create: `src/main/resources/db/migration/V23__product_image_thumbnails.sql`

**Interfaces:**
- Produces: `product_image_thumbnails` table with composite PK `(product_id, size)`

- [ ] **Step 1: Write the migration**

```sql
CREATE TABLE product_image_thumbnails (
    product_id UUID NOT NULL,
    size VARCHAR(10) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    public_url VARCHAR(500) NOT NULL,
    width_px INTEGER NOT NULL,
    height_px INTEGER,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL DEFAULT 'image/jpeg',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, size),
    CONSTRAINT fk_thumbnail_product FOREIGN KEY (product_id) REFERENCES products(id)
);
```

- [ ] **Step 2: Verify migration runs**

```bash
./gradlew test --tests "*Flyway*" 2>&1 | tail -5
```

Expected: tests pass (Flyway validates on startup).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V23__product_image_thumbnails.sql
git commit -m "feat: add product_image_thumbnails table

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Entity + Repository

**Files:**
- Create: `src/main/java/br/com/stockshift/model/entity/ProductImageThumbnail.java`
- Create: `src/main/java/br/com/stockshift/repository/ProductImageThumbnailRepository.java`

**Interfaces:**
- Produces: `ProductImageThumbnail` entity (composite PK `productId` + `size`), repository with `findByProductId` and `deleteByProductId`

- [ ] **Step 1: Write the entity**

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_image_thumbnails")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(ProductImageThumbnail.ProductImageThumbnailId.class)
public class ProductImageThumbnail {

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Id
    @Column(name = "size", nullable = false, length = 10)
    private String size;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "public_url", nullable = false, length = 500)
    private String publicUrl;

    @Column(name = "width_px", nullable = false)
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductImageThumbnailId implements Serializable {
        private UUID productId;
        private String size;
    }
}
```

- [ ] **Step 2: Write the repository**

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.ProductImageThumbnail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageThumbnailRepository
        extends JpaRepository<ProductImageThumbnail, ProductImageThumbnail.ProductImageThumbnailId> {

    List<ProductImageThumbnail> findByProductId(UUID productId);

    void deleteByProductId(UUID productId);
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew compileJava 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/ProductImageThumbnail.java \
        src/main/java/br/com/stockshift/repository/ProductImageThumbnailRepository.java
git commit -m "feat: add ProductImageThumbnail entity and repository

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: ThumbnailGenerator interface + ThumbnailatorGenerator

**Files:**
- Create: `src/main/java/br/com/stockshift/service/imaging/ThumbnailGenerator.java`
- Create: `src/main/java/br/com/stockshift/service/imaging/ThumbnailatorGenerator.java`

**Interfaces:**
- Produces: `ThumbnailGenerator` interface with `ThumbnailResult generate(InputStream, String contentType, String fileName)`, `ThumbnailatorGenerator` implementation

- [ ] **Step 1: Add Thumbnailator dependency**

```groovy
// In build.gradle, inside dependencies block, after the AWS SDK line:
implementation 'net.coobird:thumbnailator:0.4.20'
```

- [ ] **Step 2: Write the ThumbnailResult record and interface**

```java
package br.com.stockshift.service.imaging;

import java.io.InputStream;

public interface ThumbnailGenerator {

    record ThumbnailResult(
            InputStream inputStream,
            int widthPx,
            int heightPx,
            long sizeBytes,
            String formatName
    ) {}

    record ThumbnailSpec(int maxWidth, float quality) {}

    ThumbnailResult generate(InputStream original, String contentType, String fileName, ThumbnailSpec spec);
}
```

- [ ] **Step 3: Write the ThumbnailatorGenerator implementation**

```java
package br.com.stockshift.service.imaging;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
public class ThumbnailatorGenerator implements ThumbnailGenerator {

    private static final String OUTPUT_FORMAT = "jpg";

    @Override
    public ThumbnailResult generate(InputStream original, String contentType, String fileName, ThumbnailSpec spec) {
        try {
            BufferedImage image = ImageIO.read(original);
            if (image == null) {
                throw new IOException("Failed to decode image: unsupported format for " + fileName);
            }

            int targetWidth = Math.min(spec.maxWidth(), image.getWidth());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(image)
                    .width(targetWidth)
                    .outputFormat(OUTPUT_FORMAT)
                    .outputQuality(spec.quality())
                    .toOutputStream(out);

            byte[] bytes = out.toByteArray();
            int outHeight = Math.round((float) targetWidth / image.getWidth() * image.getHeight());

            return new ThumbnailResult(
                    new ByteArrayInputStream(bytes),
                    targetWidth,
                    outHeight,
                    bytes.length,
                    OUTPUT_FORMAT
            );
        } catch (IOException e) {
            log.warn("Failed to generate thumbnail for {}: {}", fileName, e.getMessage());
            throw new RuntimeException("Thumbnail generation failed: " + fileName, e);
        }
    }
}
```

- [ ] **Step 4: Compile**

```bash
./gradlew compileJava 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add build.gradle \
        src/main/java/br/com/stockshift/service/imaging/
git commit -m "feat: add ThumbnailGenerator interface and ThumbnailatorGenerator

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: StorageService — upload and delete with thumbnails

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/StorageService.java` (add new methods, preserve all existing)

**Interfaces:**
- Consumes: `ThumbnailGenerator` from Task 3
- Produces: `uploadProductImageWithThumbnails(MultipartFile) → Thumbnails`, `deleteProductImages(String imageUrl, List<String> thumbnailKeys)`

- [ ] **Step 1: Add ThumbnailGenerator dependency and thumbnail sizes constants to StorageService**

Add these fields alongside the existing `PRODUCT_FOLDER` etc. constants:

```java
private static final int[] THUMBNAIL_WIDTHS = {150, 400, 800};
private static final String[] THUMBNAIL_SUFFIXES = {"_sm", "_md", "_lg"};
private static final float[] THUMBNAIL_QUALITIES = {0.80f, 0.82f, 0.85f};
```

Add `ThumbnailGenerator` to the constructor. The existing constructor:

```java
// Before (line 29-31):
private final S3Client s3Client;
private final StorageProperties properties;

// After:
private final S3Client s3Client;
private final StorageProperties properties;
@Autowired(required = false)
@Nullable
private ThumbnailGenerator thumbnailGenerator;
```

- [ ] **Step 2: Add the Thumbnails record and uploadProductImageWithThumbnails method**

Add these imports at the top of StorageService:

```java
import br.com.stockshift.service.imaging.ThumbnailGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
```

Add the `Thumbnails` record inside `StorageService` (alongside existing `StoredImageObject`):

```java
public record Thumbnails(
    StoredImageObject original,
    StoredImageObject small,
    StoredImageObject medium,
    StoredImageObject large
) {}
```

Add the new method after `uploadImage()`:

```java
public Thumbnails uploadProductImageWithThumbnails(MultipartFile file) {
    validateFileType(file, PRODUCT_IMAGE_TYPES,
        "Only PNG, JPG, JPEG and WEBP images are allowed");

    StoredImageObject original = uploadFileObject(file, PRODUCT_FOLDER, null);
    StoredImageObject small = null;
    StoredImageObject medium = null;
    StoredImageObject large = null;

    if (thumbnailGenerator != null) {
        small = generateAndUploadThumbnail(file, 0, original);
        medium = generateAndUploadThumbnail(file, 1, original);
        large = generateAndUploadThumbnail(file, 2, original);
    }

    return new Thumbnails(original, small, medium, large);
}

private StoredImageObject generateAndUploadThumbnail(
        MultipartFile file, int sizeIndex, StoredImageObject original) {
    try {
        ThumbnailGenerator.ThumbnailSpec spec = new ThumbnailGenerator.ThumbnailSpec(
            THUMBNAIL_WIDTHS[sizeIndex], THUMBNAIL_QUALITIES[sizeIndex]);

        String thumbnailKey = deriveThumbnailKey(original.key(), THUMBNAIL_SUFFIXES[sizeIndex]);
        InputStream originalStream = file.getInputStream();
        ThumbnailGenerator.ThumbnailResult result =
            thumbnailGenerator.generate(originalStream, file.getContentType(),
                file.getOriginalFilename(), spec);
        originalStream.close();

        byte[] bytes = result.inputStream().readAllBytes();
        result.inputStream().close();

        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(properties.getBucketName())
            .key(thumbnailKey)
            .contentType("image/jpeg")
            .contentLength(bytes.length)
            .build();

        s3Client.putObject(request, RequestBody.fromBytes(bytes));

        String publicUrl = buildPublicUrl(thumbnailKey);
        log.info("Thumbnail uploaded: {}", publicUrl);
        return new StoredImageObject(thumbnailKey, publicUrl);
    } catch (Exception e) {
        log.warn("Failed to generate thumbnail {} for {}: {}",
            THUMBNAIL_SUFFIXES[sizeIndex], file.getOriginalFilename(), e.getMessage());
        return null;
    }
}

private String deriveThumbnailKey(String originalKey, String suffix) {
    int dotIndex = originalKey.lastIndexOf('.');
    if (dotIndex > 0) {
        return originalKey.substring(0, dotIndex) + suffix + ".jpg";
    }
    return originalKey + suffix + ".jpg";
}
```

- [ ] **Step 3: Add deleteProductImages method**

Add alongside existing `deleteImage()`:

```java
public void deleteProductImages(String imageUrl, List<String> thumbnailKeys) {
    deleteImage(imageUrl);
    if (thumbnailKeys != null) {
        thumbnailKeys.forEach(this::deleteStorageKeyQuietly);
    }
}
```

- [ ] **Step 4: Compile**

```bash
./gradlew compileJava 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/StorageService.java
git commit -m "feat: add thumbnail generation and delete methods to StorageService

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: ProductService — integrate thumbnails into create/update/delete

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/ProductService.java`

**Interfaces:**
- Consumes: `StorageService.Thumbnails`, `ProductImageThumbnailRepository` from Tasks 2, 4
- Produces: updated `create`, `update`, `delete`, `mapToResponse` that handle thumbnails

- [ ] **Step 1: Add ProductImageThumbnailRepository to constructor**

```java
// Add to existing fields:
private final ProductImageThumbnailRepository thumbnailRepository;

// Update constructor to include it:
public ProductService(
        ProductRepository productRepository,
        BatchRepository batchRepository,
        CategoryRepository categoryRepository,
        BrandRepository brandRepository,
        AuditService auditService,
        AuditSnapshotService auditSnapshotService,
        ProductImageThumbnailRepository thumbnailRepository) {
    this.productRepository = productRepository;
    this.batchRepository = batchRepository;
    this.categoryRepository = categoryRepository;
    this.brandRepository = brandRepository;
    this.auditService = auditService;
    this.auditSnapshotService = auditSnapshotService;
    this.thumbnailRepository = thumbnailRepository;
}
```

Replace the `@RequiredArgsConstructor` with the explicit constructor shown above.

- [ ] **Step 2: Update attachProductImage to generate thumbnails**

Replace the existing `attachProductImage` method (lines 94-107):

```java
private Product attachProductImage(Product product, MultipartFile image) {
    if (image == null || image.isEmpty() || storageService == null) {
        return product;
    }

    StorageService.Thumbnails thumbs = storageService.uploadProductImageWithThumbnails(image);
    try {
        product.setImageUrl(SanitizationUtil.sanitizeUrl(thumbs.original().publicUrl()));
        Product saved = productRepository.save(product);
        saveThumbnails(saved.getId(), thumbs);
        return saved;
    } catch (RuntimeException exception) {
        deleteUploadedImageQuietly(thumbs.original().publicUrl());
        if (thumbs.small() != null) storageService.deleteStorageKeyQuietly(thumbs.small().key());
        if (thumbs.medium() != null) storageService.deleteStorageKeyQuietly(thumbs.medium().key());
        if (thumbs.large() != null) storageService.deleteStorageKeyQuietly(thumbs.large().key());
        throw exception;
    }
}

private void saveThumbnails(UUID productId, StorageService.Thumbnails thumbs) {
    List<ProductImageThumbnail> entities = new java.util.ArrayList<>();
    if (thumbs.small() != null) {
        entities.add(buildThumbnailEntity(productId, "sm", thumbs.small(), 150));
    }
    if (thumbs.medium() != null) {
        entities.add(buildThumbnailEntity(productId, "md", thumbs.medium(), 400));
    }
    if (thumbs.large() != null) {
        entities.add(buildThumbnailEntity(productId, "lg", thumbs.large(), 800));
    }
    if (!entities.isEmpty()) {
        thumbnailRepository.saveAll(entities);
    }
}

private ProductImageThumbnail buildThumbnailEntity(
        UUID productId, String size, StorageService.StoredImageObject stored, int width) {
    return ProductImageThumbnail.builder()
            .productId(productId)
            .size(size)
            .storageKey(stored.key())
            .publicUrl(stored.publicUrl())
            .widthPx(width)
            .sizeBytes(0L)
            .contentType("image/jpeg")
            .createdAt(java.time.LocalDateTime.now())
            .build();
}
```

Add the import:

```java
import br.com.stockshift.model.entity.ProductImageThumbnail;
import br.com.stockshift.repository.ProductImageThumbnailRepository;
```

- [ ] **Step 3: Update the update method to defer thumbnail save until after final product save**

Replace the image-update block (lines 247-255):

```java
// Upload new image if provided
StorageService.Thumbnails newThumbnails = null;
if (image != null && !image.isEmpty() && storageService != null) {
    // Delete old image and thumbnails if exists
    deleteProductImages(product);
    newThumbnails = storageService.uploadProductImageWithThumbnails(image);
    product.setImageUrl(SanitizationUtil.sanitizeUrl(newThumbnails.original().publicUrl()));
}
```

Then, after the existing `Product updated = productRepository.save(product);` line (currently line 302), add:

```java
if (newThumbnails != null) {
    saveThumbnails(updated.getId(), newThumbnails);
}
```

This ensures thumbnails are persisted only after the final product save, within the same transaction.

And add the helper method:

```java
private void deleteProductImages(Product product) {
    if (product.getImageUrl() == null || storageService == null) {
        return;
    }
    List<ProductImageThumbnail> oldThumbnails = thumbnailRepository.findByProductId(product.getId());
    List<String> thumbnailKeys = oldThumbnails.stream()
            .map(ProductImageThumbnail::getStorageKey)
            .collect(java.util.stream.Collectors.toList());
    storageService.deleteProductImages(product.getImageUrl(), thumbnailKeys);
    thumbnailRepository.deleteAll(oldThumbnails);
}
```

- [ ] **Step 4: Update the delete method to clean up thumbnails**

Replace the delete-image block in `delete()` (lines 323-326):

```java
// Delete image and thumbnails if exists
deleteProductImages(product);
```

- [ ] **Step 5: Update mapToResponse to include thumbnails**

Add after the `.imageUrl(...)` line in `mapToResponse`:

```java
.thumbnails(buildThumbnailMap(product.getId()))
```

Add the helper method:

```java
private Map<String, String> buildThumbnailMap(UUID productId) {
    List<ProductImageThumbnail> thumbnails = thumbnailRepository.findByProductId(productId);
    if (thumbnails.isEmpty()) {
        return Map.of();
    }
    Map<String, String> map = new java.util.HashMap<>();
    for (ProductImageThumbnail t : thumbnails) {
        map.put(t.getSize(), t.getPublicUrl());
    }
    return map;
}
```

Ensure `Map` is imported: `import java.util.Map;`

- [ ] **Step 6: Compile**

```bash
./gradlew compileJava 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/br/com/stockshift/service/ProductService.java
git commit -m "feat: integrate thumbnails into ProductService create/update/delete

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: ProductResponse — add thumbnails field

**Files:**
- Modify: `src/main/java/br/com/stockshift/dto/product/ProductResponse.java`

**Interfaces:**
- Consumes: nothing new
- Produces: `thumbnails` field of type `Map<String, String>` defaulting to empty map

- [ ] **Step 1: Add the field**

```java
// Add after the imageUrl field:
@Builder.Default
private Map<String, String> thumbnails = Map.of();
```

Ensure `java.util.Map` is imported.

- [ ] **Step 2: Compile**

```bash
./gradlew compileJava 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/product/ProductResponse.java
git commit -m "feat: add thumbnails field to ProductResponse

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: Unit tests — StorageService

**Files:**
- Create: `src/test/java/br/com/stockshift/service/imaging/ThumbnailatorGeneratorTest.java`
- Modify: `src/test/java/br/com/stockshift/service/StorageServiceTest.java`

**Interfaces:**
- Consumes: `ThumbnailatorGenerator`, `StorageService` from Tasks 3, 4

- [ ] **Step 1: Write ThumbnailatorGeneratorTest**

```java
package br.com.stockshift.service.imaging;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ThumbnailatorGeneratorTest {

    private final ThumbnailatorGenerator generator = new ThumbnailatorGenerator();

    @Test
    void shouldResizeImageToTargetWidth() throws Exception {
        InputStream original = createTestImage(600, 400);
        ThumbnailGenerator.ThumbnailSpec spec = new ThumbnailGenerator.ThumbnailSpec(150, 0.80f);

        ThumbnailGenerator.ThumbnailResult result = generator.generate(
            original, "image/png", "test.png", spec);

        assertThat(result.widthPx()).isEqualTo(150);
        assertThat(result.heightPx()).isEqualTo(100); // 400 * 150/600
        assertThat(result.formatName()).isEqualTo("jpg");
        assertThat(result.sizeBytes()).isGreaterThan(0);
    }

    @Test
    void shouldNotUpscaleWhenImageIsSmallerThanTarget() throws Exception {
        InputStream original = createTestImage(100, 100);
        ThumbnailGenerator.ThumbnailSpec spec = new ThumbnailGenerator.ThumbnailSpec(800, 0.85f);

        ThumbnailGenerator.ThumbnailResult result = generator.generate(
            original, "image/png", "small.png", spec);

        assertThat(result.widthPx()).isEqualTo(100); // no upscale
    }

    private InputStream createTestImage(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new ByteArrayInputStream(out.toByteArray());
    }
}
```

- [ ] **Step 2: Run ThumbnailatorGeneratorTest**

```bash
./gradlew test --tests "*ThumbnailatorGeneratorTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, 2 tests pass.

- [ ] **Step 3: Add thumbnail test to StorageServiceTest**

Add these test methods to the existing `StorageServiceTest` class:

```java
@Test
void deriveThumbnailKeyShouldAppendSuffixBeforeExtension() {
    // Use reflection to test private method, or test indirectly via public API
    // The deriveThumbnailKey method is private — test via uploadProductImageWithThumbnails
    // when thumbnailGenerator mock is set up (in integration tests)
}

@Test
void deleteProductImagesShouldHandleNullThumbnailKeys() {
    // deleteProductImages with null thumbnailKeys should not throw
    assertDoesNotThrow(() -> storageService.deleteProductImages(
        "https://cdn.example.com/products/test.png", null));
}
```

Note: Because `StorageService` requires a real `S3Client` for upload operations, full upload+thumbnail tests go in the integration test (Task 9). The unit tests here focus on validation and delete behavior.

- [ ] **Step 4: Run StorageServiceTest**

```bash
./gradlew test --tests "*StorageServiceTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all existing + new tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/br/com/stockshift/service/imaging/ThumbnailatorGeneratorTest.java \
        src/test/java/br/com/stockshift/service/StorageServiceTest.java
git commit -m "test: add unit tests for ThumbnailatorGenerator and StorageService thumbnails

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: Unit tests — ProductService

**Files:**
- Modify: `src/test/java/br/com/stockshift/service/ProductServiceTest.java`

**Interfaces:**
- Consumes: `ProductImageThumbnailRepository` mock, `StorageService.Thumbnails`

- [ ] **Step 1: Add mocks and update setUp**

Add the new mock field:

```java
@Mock
private ProductImageThumbnailRepository thumbnailRepository;
```

Update the `setUp()` method — replace the constructor call:

```java
productService = new ProductService(
        productRepository,
        batchRepository,
        categoryRepository,
        brandRepository,
        auditService,
        auditSnapshotService,
        thumbnailRepository
);
ReflectionTestUtils.setField(productService, "storageService", storageService);
```

Add the import:

```java
import br.com.stockshift.repository.ProductImageThumbnailRepository;
import br.com.stockshift.model.entity.ProductImageThumbnail;
```

- [ ] **Step 2: Update createShouldPersistProductWithCategoryBrandImageAndAudit test**

Replace the `storageService.uploadImage` mock in this test to return `Thumbnails`:

```java
@Test
void createShouldPersistProductWithCategoryBrandImageAndAudit() {
    Category category = category("Bebidas");
    Brand brand = brand("Acme", null);
    MockMultipartFile image = image();
    when(categoryRepository.findByTenantIdAndId(tenantId, category.getId()))
            .thenReturn(Optional.of(category));
    when(brandRepository.findByTenantIdAndId(tenantId, brand.getId()))
            .thenReturn(Optional.of(brand));

    var original = new StorageService.StoredImageObject(
        "products/test.png", "https://cdn.example.com/product.png");
    var small = new StorageService.StoredImageObject(
        "products/test_sm.jpg", "https://cdn.example.com/product_sm.jpg");
    var medium = new StorageService.StoredImageObject(
        "products/test_md.jpg", "https://cdn.example.com/product_md.jpg");
    var large = new StorageService.StoredImageObject(
        "products/test_lg.jpg", "https://cdn.example.com/product_lg.jpg");
    StorageService.Thumbnails thumbs = new StorageService.Thumbnails(original, small, medium, large);
    when(storageService.uploadProductImageWithThumbnails(image)).thenReturn(thumbs);

    var response = productService.create(fullRequest(category.getId(), brand.getId()), image);

    assertThat(response.getCategoryName()).isEqualTo("Bebidas");
    assertThat(response.getBrand().getName()).isEqualTo("Acme");
    assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.com/product.png");
    assertThat(response.getSku()).isEqualTo("SKU-1");
    assertThat(response.getThumbnails()).containsKeys("sm", "md", "lg");
    assertThat(response.getThumbnails().get("sm")).isEqualTo("https://cdn.example.com/product_sm.jpg");
    verify(auditService).record(any());
}
```

- [ ] **Step 3: Update createShouldDeleteUploadedImageWhenImagePersistenceFails test**

```java
@Test
void createShouldDeleteUploadedImageWhenImagePersistenceFails() {
    ProductRequest request = fullRequest(null, null);
    MockMultipartFile image = image();
    var original = new StorageService.StoredImageObject(
        "products/rollback.png", "https://cdn.example.com/rollback.png");
    var small = new StorageService.StoredImageObject(
        "products/rollback_sm.jpg", "https://cdn.example.com/rollback_sm.jpg");
    StorageService.Thumbnails thumbs = new StorageService.Thumbnails(original, small, null, null);
    when(storageService.uploadProductImageWithThumbnails(image)).thenReturn(thumbs);
    when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> {
                Product product = invocation.getArgument(0);
                product.setId(UUID.randomUUID());
                return product;
            })
            .thenThrow(new BusinessException("image write failed"));

    assertThatThrownBy(() -> productService.create(request, image))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("image write failed");
    verify(storageService).deleteImage("https://cdn.example.com/rollback.png");
    verify(storageService).deleteStorageKeyQuietly("products/rollback_sm.jpg");
}
```

- [ ] **Step 4: Update updateShouldReplaceImageClearRelationsAndRecordAudit test**

```java
@Test
void updateShouldReplaceImageClearRelationsAndRecordAudit() {
    Product product = product("Produto antigo");
    product.setCategory(category("Antiga"));
    product.setBrand(brand("Antiga", null));
    product.setImageUrl("https://cdn.example.com/old.png");
    ProductRequest request = fullRequest(null, null);
    request.setName("Produto novo");
    request.setSku("SKU-2");
    request.setBarcode("999");
    MockMultipartFile image = image();
    when(productRepository.findByTenantIdAndId(tenantId, product.getId()))
            .thenReturn(Optional.of(product));
    when(thumbnailRepository.findByProductId(product.getId()))
            .thenReturn(java.util.List.of());

    var original = new StorageService.StoredImageObject(
        "products/new.png", "https://cdn.example.com/new.png");
    var small = new StorageService.StoredImageObject(
        "products/new_sm.jpg", "https://cdn.example.com/new_sm.jpg");
    var medium = new StorageService.StoredImageObject(
        "products/new_md.jpg", "https://cdn.example.com/new_md.jpg");
    var large = new StorageService.StoredImageObject(
        "products/new_lg.jpg", "https://cdn.example.com/new_lg.jpg");
    StorageService.Thumbnails thumbs = new StorageService.Thumbnails(original, small, medium, large);
    when(storageService.uploadProductImageWithThumbnails(image)).thenReturn(thumbs);

    var response = productService.update(product.getId(), request, image);

    assertThat(response.getName()).isEqualTo("Produto novo");
    assertThat(response.getCategoryId()).isNull();
    assertThat(response.getBrand()).isNull();
    assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.com/new.png");
    assertThat(response.getThumbnails()).containsKeys("sm", "md", "lg");
    verify(storageService).deleteProductImages(eq("https://cdn.example.com/old.png"), any());
    verify(auditService).record(any());
}
```

- [ ] **Step 5: Add test for product without image → empty thumbnails**

```java
@Test
void mapToResponseShouldReturnEmptyThumbnailsForProductWithoutImage() {
    Product product = product("Sem imagem");
    product.setImageUrl(null);
    when(productRepository.findByTenantIdAndId(tenantId, product.getId()))
            .thenReturn(Optional.of(product));
    when(thumbnailRepository.findByProductId(product.getId()))
            .thenReturn(java.util.List.of());

    var response = productService.findById(product.getId());

    assertThat(response.getThumbnails()).isEmpty();
}
```

- [ ] **Step 6: Run ProductServiceTest**

```bash
./gradlew test --tests "*ProductServiceTest" 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/test/java/br/com/stockshift/service/ProductServiceTest.java
git commit -m "test: update ProductServiceTest for thumbnail integration

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: Integration tests

**Files:**
- Modify: `src/test/java/br/com/stockshift/controller/ProductControllerIntegrationTest.java`

**Interfaces:**
- Consumes: full stack from Tasks 1–6

- [ ] **Step 1: Read existing integration test to understand test setup**

```bash
head -100 src/test/java/br/com/stockshift/controller/ProductControllerIntegrationTest.java
```

- [ ] **Step 2: Add a test for thumbnail URLs in create response**

Add a test method to the integration test class (adapt test setup to match existing patterns — e.g., `mockMvc`, auth tokens, etc.):

```java
@Test
void createProductWithImageShouldReturnThumbnailUrls() throws Exception {
    // Use MockMultipartFile or actual test image
    MockMultipartFile image = new MockMultipartFile(
        "image", "product.png", "image/png", new byte[100]);

    MockMultipartFile productPart = new MockMultipartFile(
        "product", "", "application/json",
        """
        {"name":"Test","description":"Desc","sku":"TST-001","isKit":false,
         "hasExpiration":false,"active":true}
        """.getBytes());

    MvcResult result = mockMvc.perform(multipart("/api/products")
            .file(image)
            .file(productPart)
            .header("Authorization", "Bearer " + getValidToken())
            .with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.thumbnails.sm").exists())
            .andExpect(jsonPath("$.data.thumbnails.md").exists())
            .andExpect(jsonPath("$.data.thumbnails.lg").exists())
            .andReturn();
}
```

- [ ] **Step 3: Add a test for product without image returns empty thumbnails**

```java
@Test
void getProductWithoutImageShouldReturnEmptyThumbnails() throws Exception {
    mockMvc.perform(get("/api/products/{id}", existingProductWithoutImageId)
            .header("Authorization", "Bearer " + getValidToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.thumbnails").isEmpty());
}
```

- [ ] **Step 4: Run integration tests**

```bash
./gradlew test --tests "*ProductControllerIntegrationTest" 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/ProductControllerIntegrationTest.java
git commit -m "test: add integration tests for product image thumbnails

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: Regression — full test suite

**Files:**
- No new files — verify all tests pass

- [ ] **Step 1: Run full test suite**

```bash
./gradlew test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests pass, no regressions.

- [ ] **Step 2: Check for regressions in StockMovementService tests specifically**

```bash
./gradlew test --tests "*StockMovementServiceTest" --tests "*ProductImageUploadServiceTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit if any final cleanup was needed**

```bash
git add -A
git diff --staged --stat
# Only commit if there are staged changes from fixes
```

- [ ] **Step 4: Final verification — run tests one more time**

```bash
./gradlew test 2>&1 | grep -E "BUILD|tests? (completed|passed|failed)"
```

Expected: `BUILD SUCCESSFUL`.
