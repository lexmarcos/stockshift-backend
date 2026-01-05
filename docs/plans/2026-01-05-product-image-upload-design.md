# Product Image Upload Design

**Date:** 2026-01-05
**Status:** Approved
**Endpoints:** `/api/products` (POST), `/api/batches/with-product` (POST)

## Overview

Add image upload functionality to product creation endpoints. Images will be stored in Supabase Storage (S3-compatible) and the public URL will be stored in the database.

## Requirements

- **Format:** MultipartFile (form-data)
- **Required:** Optional - products can be created without images
- **Quantity:** Single image per product
- **Validations:**
  - Accept only PNG, JPG, JPEG, WEBP formats
  - Generate unique filename to prevent conflicts
- **Configuration:** Environment variables for Supabase credentials

## Architecture

### Component Layers

1. **Controllers:** Receive multipart requests with image and product data
2. **Services:** Coordinate business logic and delegate storage operations
3. **StorageService:** Abstract storage operations (upload, delete, validate)
4. **S3Client:** AWS SDK client configured for Supabase Storage

### Data Flow

```
Frontend → Controller (multipart)
              ↓
        ProductService
              ↓
        Validate data
              ↓
        StorageService.upload()
              ↓
        Supabase Storage (S3)
              ↓
        Return image URL
              ↓
        ProductRepository.save()
              ↓
        ProductResponse
```

## Implementation Details

### 1. Dependencies

Add to `build.gradle`:

```gradle
implementation 'software.amazon.awssdk:s3:2.20.26'
```

### 2. Configuration

**StorageProperties.java:**

```java
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

**application.yml:**

```yaml
storage:
  endpoint: ${STORAGE_ENDPOINT:}
  access-key: ${STORAGE_ACCESS_KEY:}
  secret-key: ${STORAGE_SECRET_KEY:}
  bucket-name: ${STORAGE_BUCKET_NAME:product-images}
  region: ${STORAGE_REGION:us-east-1}
  public-url: ${STORAGE_PUBLIC_URL:}
```

**StorageConfig.java:**

```java
@Configuration
@RequiredArgsConstructor
public class StorageConfig {
    private final StorageProperties properties;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .endpointOverride(URI.create(properties.getEndpoint()))
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(() -> AwsBasicCredentials.create(
                properties.getAccessKey(),
                properties.getSecretKey()
            ))
            .build();
    }
}
```

### 3. StorageService

**Core Methods:**

```java
@Service
@RequiredArgsConstructor
public class StorageService {
    private final S3Client s3Client;
    private final StorageProperties properties;

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/png", "image/jpeg", "image/jpg", "image/webp"
    );
    private static final String FOLDER = "products/";

    public String uploadImage(MultipartFile file) {
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

        return properties.getPublicUrl() + "/" + key;
    }

    public void deleteImage(String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith(properties.getPublicUrl())) {
            String key = extractKeyFromUrl(imageUrl);
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .build();
            s3Client.deleteObject(request);
        }
    }

    private void validateFileType(MultipartFile file) {
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new InvalidFileTypeException(
                "Only PNG, JPG, JPEG and WEBP images are allowed"
            );
        }
    }

    private String generateUniqueFileName(String originalName) {
        String extension = getFileExtension(originalName);
        return UUID.randomUUID().toString() + extension;
    }
}
```

### 4. Database Changes

**Entity Product:**

```java
@Column(name = "image_url", length = 500)
private String imageUrl;
```

**DTOs:**

- Add `imageUrl` field to `ProductRequest`, `ProductResponse`, and `ProductBatchRequest`

**Migration (Flyway):**

```sql
-- V{next}_add_image_url_to_products.sql
ALTER TABLE products
ADD COLUMN image_url VARCHAR(500);

CREATE INDEX idx_products_image_url ON products(image_url);

COMMENT ON COLUMN products.image_url IS 'Public URL of the product image stored in Supabase Storage';
```

### 5. Controller Changes

**ProductController:**

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

**BatchController:**

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

### 6. Service Changes

**ProductService:**

```java
@Transactional
public ProductResponse create(ProductRequest request, MultipartFile image) {
    // Upload image if provided
    if (image != null && !image.isEmpty()) {
        String imageUrl = storageService.uploadImage(image);
        request.setImageUrl(imageUrl);
    }

    // Create and save product
    Product product = new Product();
    // ... map fields ...
    product.setImageUrl(request.getImageUrl());

    Product saved = productRepository.save(product);
    return mapToResponse(saved);
}

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

    // ... update other fields ...
    return mapToResponse(productRepository.save(product));
}

@Transactional
public void delete(UUID id) {
    Product product = findEntityById(id);

    // Delete image if exists
    if (product.getImageUrl() != null) {
        storageService.deleteImage(product.getImageUrl());
    }

    // ... soft delete ...
}
```

**BatchService:**

```java
@Transactional
public ProductBatchResponse createWithProduct(
        ProductBatchRequest request, MultipartFile image) {

    // Upload image if provided
    if (image != null && !image.isEmpty()) {
        String imageUrl = storageService.uploadImage(image);
        request.setImageUrl(imageUrl);
    }

    // Create product
    ProductRequest productRequest = mapToProductRequest(request);
    Product product = createProduct(productRequest);

    // Create batch
    Batch batch = createBatch(request, product);

    return mapToResponse(product, batch);
}
```

### 7. Error Handling

**Custom Exceptions:**

```java
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidFileTypeException extends RuntimeException {
    public InvalidFileTypeException(String message) {
        super(message);
    }
}

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**StorageService Error Handling:**

```java
public String uploadImage(MultipartFile file) {
    try {
        // ... upload logic ...
    } catch (IOException e) {
        throw new StorageException("Failed to read image file", e);
    } catch (S3Exception e) {
        throw new StorageException("Failed to upload image to storage", e);
    }
}
```

## Environment Variables

Required configuration:

```bash
STORAGE_ENDPOINT=https://your-project.supabase.co/storage/v1/s3
STORAGE_ACCESS_KEY=your-access-key
STORAGE_SECRET_KEY=your-secret-key
STORAGE_BUCKET_NAME=product-images
STORAGE_REGION=us-east-1
STORAGE_PUBLIC_URL=https://your-project.supabase.co/storage/v1/object/public/product-images
```

## Testing Considerations

1. **Unit Tests:** Test StorageService methods with mocked S3Client
2. **Integration Tests:** Test full flow with localstack or Supabase test bucket
3. **Validation Tests:** Test file type validation and error handling
4. **Cleanup Tests:** Verify image deletion on product update/delete

## Security Considerations

1. File type validation prevents malicious file uploads
2. Unique filenames prevent path traversal attacks
3. Credentials stored in environment variables, not hardcoded
4. Public URLs are read-only (bucket configuration)

## Future Enhancements

- Image resizing/optimization (thumbnails)
- Multiple images per product (gallery)
- Image size validation
- CDN integration
- Image compression
