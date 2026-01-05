# Product Image Upload Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add image upload functionality to product creation endpoints with Supabase Storage (S3-compatible) integration.

**Architecture:** MultipartFile uploads → StorageService → S3Client → Supabase Storage. Images stored in cloud, URLs saved in database. Optional image field allows products without images.

**Tech Stack:** AWS SDK S3, Spring Boot MultipartFile, PostgreSQL, Flyway migrations.

---

## Task 1: Add AWS S3 Dependency

**Files:**
- Modify: `build.gradle`

**Step 1: Add AWS S3 dependency to build.gradle**

Add after existing dependencies:

```gradle
implementation 'software.amazon.awssdk:s3:2.20.26'
```

**Step 2: Sync Gradle dependencies**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL with S3 SDK downloaded

**Step 3: Commit**

```bash
git add build.gradle
git commit -m "build: add AWS SDK S3 dependency for Supabase Storage"
```

---

## Task 2: Create Storage Configuration Classes

**Files:**
- Create: `src/main/java/br/com/stockshift/config/StorageProperties.java`
- Create: `src/main/java/br/com/stockshift/config/StorageConfig.java`
- Modify: `src/main/resources/application.yml`

**Step 1: Create StorageProperties class**

```java
package br.com.stockshift.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "storage")
@Data
public class StorageProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String region = "us-east-1";
    private String publicUrl;
}
```

**Step 2: Create StorageConfig class**

```java
package br.com.stockshift.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "storage", name = "endpoint")
public class StorageConfig {
    private final StorageProperties properties;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
            properties.getAccessKey(),
            properties.getSecretKey()
        );

        return S3Client.builder()
            .endpointOverride(URI.create(properties.getEndpoint()))
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build();
    }
}
```

**Step 3: Add storage configuration to application.yml**

Add after `cors:` section:

```yaml
storage:
  endpoint: ${STORAGE_ENDPOINT:}
  access-key: ${STORAGE_ACCESS_KEY:}
  secret-key: ${STORAGE_SECRET_KEY:}
  bucket-name: ${STORAGE_BUCKET_NAME:product-images}
  region: ${STORAGE_REGION:us-east-1}
  public-url: ${STORAGE_PUBLIC_URL:}
```

**Step 4: Verify configuration loads**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/config/StorageProperties.java src/main/java/br/com/stockshift/config/StorageConfig.java src/main/resources/application.yml
git commit -m "feat: add storage configuration for Supabase S3"
```

---

## Task 3: Create Custom Exceptions

**Files:**
- Create: `src/main/java/br/com/stockshift/exception/InvalidFileTypeException.java`
- Create: `src/main/java/br/com/stockshift/exception/StorageException.java`

**Step 1: Create InvalidFileTypeException**

```java
package br.com.stockshift.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidFileTypeException extends RuntimeException {
    public InvalidFileTypeException(String message) {
        super(message);
    }
}
```

**Step 2: Create StorageException**

```java
package br.com.stockshift.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/exception/InvalidFileTypeException.java src/main/java/br/com/stockshift/exception/StorageException.java
git commit -m "feat: add storage-related custom exceptions"
```

---

## Task 4: Create StorageService with Tests

**Files:**
- Create: `src/main/java/br/com/stockshift/service/StorageService.java`
- Create: `src/test/java/br/com/stockshift/service/StorageServiceTest.java`

**Step 1: Write test for file type validation**

```java
package br.com.stockshift.service;

import br.com.stockshift.config.StorageProperties;
import br.com.stockshift.exception.InvalidFileTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private StorageProperties properties;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(s3Client, properties);
    }

    @Test
    void shouldRejectInvalidFileType() {
        MultipartFile file = new MockMultipartFile(
            "file",
            "test.pdf",
            "application/pdf",
            "test data".getBytes()
        );

        assertThrows(InvalidFileTypeException.class, () -> {
            storageService.uploadImage(file);
        });
    }

    @Test
    void shouldAcceptValidImageTypes() {
        String[] validTypes = {"image/png", "image/jpeg", "image/jpg", "image/webp"};

        for (String type : validTypes) {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                type,
                "test data".getBytes()
            );

            // Should not throw exception during validation
            assertDoesNotThrow(() -> {
                try {
                    storageService.uploadImage(file);
                } catch (StorageException e) {
                    // Ignore storage exceptions, we're only testing validation
                }
            });
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests StorageServiceTest`
Expected: FAIL - StorageService class not found

**Step 3: Create StorageService with minimal implementation**

```java
package br.com.stockshift.service;

import br.com.stockshift.config.StorageProperties;
import br.com.stockshift.exception.InvalidFileTypeException;
import br.com.stockshift.exception.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(S3Client.class)
public class StorageService {

    private final S3Client s3Client;
    private final StorageProperties properties;

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/png", "image/jpeg", "image/jpg", "image/webp"
    );
    private static final String FOLDER = "products/";

    public String uploadImage(MultipartFile file) {
        try {
            validateFileType(file);
            String fileName = generateUniqueFileName(file.getOriginalFilename());
            String key = FOLDER + fileName;

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .contentType(file.getContentType())
                .build();

            s3Client.putObject(request,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String imageUrl = properties.getPublicUrl() + "/" + key;
            log.info("Image uploaded successfully: {}", imageUrl);
            return imageUrl;

        } catch (IOException e) {
            log.error("Failed to read image file", e);
            throw new StorageException("Failed to read image file", e);
        } catch (S3Exception e) {
            log.error("Failed to upload image to storage", e);
            throw new StorageException("Failed to upload image to storage", e);
        }
    }

    public void deleteImage(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(properties.getPublicUrl())) {
            return;
        }

        try {
            String key = extractKeyFromUrl(imageUrl);
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .build();

            s3Client.deleteObject(request);
            log.info("Image deleted successfully: {}", imageUrl);

        } catch (S3Exception e) {
            log.error("Failed to delete image from storage: {}", imageUrl, e);
            throw new StorageException("Failed to delete image from storage", e);
        }
    }

    private void validateFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileTypeException("File cannot be empty");
        }

        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidFileTypeException(
                "Only PNG, JPG, JPEG and WEBP images are allowed. Got: " + contentType
            );
        }
    }

    private String generateUniqueFileName(String originalName) {
        String extension = getFileExtension(originalName);
        return UUID.randomUUID().toString() + extension;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    private String extractKeyFromUrl(String imageUrl) {
        String publicUrl = properties.getPublicUrl();
        if (imageUrl.startsWith(publicUrl)) {
            return imageUrl.substring(publicUrl.length() + 1);
        }
        throw new IllegalArgumentException("Invalid image URL: " + imageUrl);
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests StorageServiceTest`
Expected: PASS - 2 tests passed

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/service/StorageService.java src/test/java/br/com/stockshift/service/StorageServiceTest.java
git commit -m "feat: add StorageService with file validation"
```

---

## Task 5: Add Database Migration for imageUrl

**Files:**
- Create: `src/main/resources/db/migration/V10__add_image_url_to_products.sql`

**Step 1: Create migration file**

```sql
-- Add image_url column to products table
ALTER TABLE products
ADD COLUMN image_url VARCHAR(500);

-- Add index for faster lookups
CREATE INDEX idx_products_image_url ON products(image_url);

-- Add comment
COMMENT ON COLUMN products.image_url IS 'Public URL of the product image stored in Supabase Storage';
```

**Step 2: Run migration**

Run: `./gradlew flywayMigrate` (requires database running)
Expected: Migration V10 applied successfully
Note: Skip if database not available, will run during integration tests

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V10__add_image_url_to_products.sql
git commit -m "feat: add image_url column to products table"
```

---

## Task 6: Update Product Entity and DTOs

**Files:**
- Modify: `src/main/java/br/com/stockshift/model/entity/Product.java`
- Modify: `src/main/java/br/com/stockshift/dto/product/ProductRequest.java`
- Modify: `src/main/java/br/com/stockshift/dto/product/ProductResponse.java`
- Modify: `src/main/java/br/com/stockshift/dto/warehouse/ProductBatchRequest.java`

**Step 1: Add imageUrl to Product entity**

Add after `active` field (around line 58):

```java
@Column(name = "image_url", length = 500)
private String imageUrl;
```

**Step 2: Add imageUrl to ProductRequest**

Add after `active` field (around line 33):

```java
private String imageUrl;
```

**Step 3: Add imageUrl to ProductResponse**

Add after `active` field (around line 31):

```java
private String imageUrl;
```

**Step 4: Add imageUrl to ProductBatchRequest**

Add after `hasExpiration` field (around line 35):

```java
private String imageUrl;
```

**Step 5: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/Product.java src/main/java/br/com/stockshift/dto/product/ProductRequest.java src/main/java/br/com/stockshift/dto/product/ProductResponse.java src/main/java/br/com/stockshift/dto/warehouse/ProductBatchRequest.java
git commit -m "feat: add imageUrl field to Product entity and DTOs"
```

---

## Task 7: Update ProductService to Handle Image Upload

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/ProductService.java`

**Step 1: Add StorageService dependency**

Add field in ProductService class:

```java
private final StorageService storageService;
```

**Step 2: Update create method signature**

Find the `create` method and update signature:

```java
@Transactional
public ProductResponse create(ProductRequest request, MultipartFile image) {
    // Upload image if provided
    if (image != null && !image.isEmpty()) {
        String imageUrl = storageService.uploadImage(image);
        request.setImageUrl(imageUrl);
    }

    // Continue with existing product creation logic
    Product product = new Product();
    // ... existing mapping code ...
    product.setImageUrl(request.getImageUrl());

    // ... rest of existing code ...
}
```

**Step 3: Update overloaded create method without image**

Add overloaded method for backward compatibility:

```java
@Transactional
public ProductResponse create(ProductRequest request) {
    return create(request, null);
}
```

**Step 4: Update mapToEntity to include imageUrl**

Find the `mapToEntity` or similar mapping method and add:

```java
product.setImageUrl(request.getImageUrl());
```

**Step 5: Update mapToResponse to include imageUrl**

Find the `mapToResponse` method and ensure imageUrl is included:

```java
.imageUrl(product.getImageUrl())
```

**Step 6: Update update method to handle image replacement**

Find the `update` method and add image handling:

```java
@Transactional
public ProductResponse update(UUID id, ProductRequest request, MultipartFile image) {
    Product product = findEntityById(id);

    // Upload new image if provided
    if (image != null && !image.isEmpty()) {
        // Delete old image if exists
        if (product.getImageUrl() != null) {
            storageService.deleteImage(product.getImageUrl());
        }
        String imageUrl = storageService.uploadImage(image);
        product.setImageUrl(imageUrl);
    }

    // ... rest of update logic ...
}
```

Add overloaded method:

```java
@Transactional
public ProductResponse update(UUID id, ProductRequest request) {
    return update(id, request, null);
}
```

**Step 7: Update delete method to cleanup images**

Find the `delete` method and add:

```java
@Transactional
public void delete(UUID id) {
    Product product = findEntityById(id);

    // Delete image if exists
    if (product.getImageUrl() != null) {
        storageService.deleteImage(product.getImageUrl());
    }

    // ... rest of existing soft delete logic ...
}
```

**Step 8: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 9: Commit**

```bash
git add src/main/java/br/com/stockshift/service/ProductService.java
git commit -m "feat: integrate image upload in ProductService"
```

---

## Task 8: Update ProductController for Multipart

**Files:**
- Modify: `src/main/java/br/com/stockshift/controller/ProductController.java`

**Step 1: Update create endpoint**

Find the `create` method (around line 32) and update:

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasAnyAuthority('PRODUCT_CREATE', 'ROLE_ADMIN')")
@Operation(summary = "Create a new product")
public ResponseEntity<ApiResponse<ProductResponse>> create(
        @RequestPart("product") @Valid ProductRequest request,
        @RequestPart(value = "image", required = false) MultipartFile image) {

    ProductResponse response = productService.create(request, image);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Product created successfully", response));
}
```

**Step 2: Update update endpoint**

Find the `update` method (around line 97) and update:

```java
@PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasAnyAuthority('PRODUCT_UPDATE', 'ROLE_ADMIN')")
@Operation(summary = "Update product")
public ResponseEntity<ApiResponse<ProductResponse>> update(
        @PathVariable UUID id,
        @RequestPart("product") @Valid ProductRequest request,
        @RequestPart(value = "image", required = false) MultipartFile image) {

    ProductResponse response = productService.update(id, request, image);
    return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
}
```

**Step 3: Add missing import**

Add at top with other imports:

```java
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
```

**Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/ProductController.java
git commit -m "feat: add multipart support to ProductController"
```

---

## Task 9: Update BatchService for Image Upload

**Files:**
- Modify: `src/main/java/br/com/stockshift/service/BatchService.java`

**Step 1: Add StorageService dependency**

Add field in BatchService class:

```java
private final StorageService storageService;
```

**Step 2: Update createWithProduct method**

Find the `createWithProduct` method and update signature:

```java
@Transactional
public ProductBatchResponse createWithProduct(ProductBatchRequest request, MultipartFile image) {
    // Upload image if provided
    if (image != null && !image.isEmpty()) {
        String imageUrl = storageService.uploadImage(image);
        request.setImageUrl(imageUrl);
    }

    // Continue with existing logic
    // ... rest of existing code ...
}
```

**Step 3: Add overloaded method for backward compatibility**

```java
@Transactional
public ProductBatchResponse createWithProduct(ProductBatchRequest request) {
    return createWithProduct(request, null);
}
```

**Step 4: Ensure imageUrl is mapped to Product**

In the method that creates the Product from ProductBatchRequest, ensure:

```java
product.setImageUrl(request.getImageUrl());
```

**Step 5: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/br/com/stockshift/service/BatchService.java
git commit -m "feat: integrate image upload in BatchService"
```

---

## Task 10: Update BatchController for Multipart

**Files:**
- Modify: `src/main/java/br/com/stockshift/controller/BatchController.java`

**Step 1: Update createWithProduct endpoint**

Find the `createWithProduct` method (around line 43) and update:

```java
@PostMapping(value = "/with-product", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasAnyAuthority('BATCH_CREATE', 'PRODUCT_CREATE', 'ROLE_ADMIN')")
@Operation(summary = "Create a new product with initial stock in warehouse")
public ResponseEntity<ApiResponse<ProductBatchResponse>> createWithProduct(
        @RequestPart("product") @Valid ProductBatchRequest request,
        @RequestPart(value = "image", required = false) MultipartFile image) {

    ProductBatchResponse response = batchService.createWithProduct(request, image);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Product and batch created successfully", response));
}
```

**Step 2: Add missing imports**

Add at top with other imports:

```java
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/br/com/stockshift/controller/BatchController.java
git commit -m "feat: add multipart support to BatchController"
```

---

## Task 11: Build and Verify

**Step 1: Run full build**

Run: `./gradlew clean build -x test`
Expected: BUILD SUCCESSFUL

**Step 2: Run unit tests**

Run: `./gradlew test --tests StorageServiceTest`
Expected: All tests passing

**Step 3: Review changes**

Run: `git log --oneline -11`
Expected: 11 commits for this feature

**Step 4: Final commit if needed**

If any fixes were needed, commit them:

```bash
git add .
git commit -m "fix: final adjustments for image upload feature"
```

---

## Testing Checklist

After implementation, verify:

- [ ] StorageService unit tests pass
- [ ] Product can be created without image (optional field)
- [ ] Product can be created with image (uploads to S3)
- [ ] Invalid file types are rejected (PDF, etc.)
- [ ] Valid image types are accepted (PNG, JPG, WEBP)
- [ ] Product update with new image deletes old image
- [ ] Product delete removes image from storage
- [ ] BatchController /with-product works with image
- [ ] imageUrl is returned in ProductResponse
- [ ] Database migration adds image_url column

## Environment Setup for Testing

Create `.env` file or set environment variables:

```bash
STORAGE_ENDPOINT=https://your-project.supabase.co/storage/v1/s3
STORAGE_ACCESS_KEY=your-access-key
STORAGE_SECRET_KEY=your-secret-key
STORAGE_BUCKET_NAME=product-images
STORAGE_REGION=us-east-1
STORAGE_PUBLIC_URL=https://your-project.supabase.co/storage/v1/object/public/product-images
```

## Rollback Plan

If issues arise:

1. Revert migrations: `./gradlew flywayClean` (development only)
2. Remove S3 dependency from build.gradle
3. Git revert commits in reverse order
4. Rebuild: `./gradlew clean build`

## Documentation Updates Needed

After implementation:

- [ ] Update API documentation with multipart examples
- [ ] Document environment variables in README
- [ ] Add Postman/curl examples for image upload
- [ ] Update frontend integration guide

---

**Implementation complete!** All endpoints now support optional image uploads with proper validation and storage management.
