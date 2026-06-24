# Product Image Processing Job — Design Spec

**Date:** 2026-06-24
**Status:** Draft
**Decision:** One-shot REST endpoint that downloads, compresses (>700KB originals), and generates thumbnails for existing products.

## Context

Thumbnail generation was added at upload time (see `2026-06-23-product-image-thumbnails-design.md`). Existing products uploaded before that feature have no thumbnails. Additionally, some original images may exceed 700KB — wasting storage and bandwidth.

### Current state
- `StorageService.uploadProductImageWithThumbnails()` generates 3 thumbnails at upload
- `product_image_thumbnails` table stores thumbnail metadata
- Products without thumbnails return empty `thumbnails` map — frontend falls back to `imageUrl`
- `StorageService` has no download/head capability (upload + delete only)

## Goals

1. One-shot endpoint to process existing product images (no scheduled job)
2. Compress originals > 700KB (JPEG quality 80%, same dimensions)
3. Generate missing thumbnails (150px, 400px, 800px)
4. One product failure does not block others
5. Compressed original upload is safe (does not overwrite until success confirmed)

## Non-goals

- Scheduled/cron processing (one-shot only)
- WebP/AVIF format conversion
- Processing products that have no `image_url`
- Streaming/chunked HTTP response with progress events

---

## New StorageService Capabilities

Two new methods needed:

```java
public HeadObjectResult headObject(String key);
public byte[] getObject(String key);

public record HeadObjectResult(long sizeBytes, String contentType) {}
```

Use `S3Client.headObject()` and `S3Client.getObject()`. `headObject` allows checking size before downloading — avoids unnecessary downloads for images already ≤ 700KB.

---

## Processing Pipeline

### Per-product algorithm

```
1. product.imageUrl == null? → SKIP (count as skipped)
2. storageKey = extractKeyFromUrl(imageUrl)
3. headResult = headObject(storageKey)
4. Has thumbnails AND headResult.sizeBytes ≤ 700KB? → SKIP (already good)
5. headResult.sizeBytes > 700KB?
   → originalBytes = getObject(storageKey)
   → compressedBytes = compress(originalBytes)
   → upload compressedBytes to TEMP key
   → copyObject(tempKey → storageKey)  // safe overwrite
   → delete temp key
6. Generate thumbnails from (compressedBytes or originalBytes) via ThumbnailGenerator × 3
7. Upload thumbnails to R2 (products/{uuid}_sm.jpg, etc.)
8. Upsert product_image_thumbnails rows (delete existing + insert new)
```

### Compression

```java
BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
ByteArrayOutputStream out = new ByteArrayOutputStream();
Thumbnails.of(image)
    .scale(1.0)              // same dimensions
    .outputFormat("jpg")
    .outputQuality(0.80)
    .toOutputStream(out);
return out.toByteArray();
```

- `scale(1.0)` — preserves original dimensions, only reduces quality
- Always outputs JPEG — transparent PNGs become white-background JPEGs
- Quality 80% — 40-60% size reduction with imperceptible quality loss

---

## API Contract

### Endpoint

```
POST /api/admin/products/process-images?productId={uuid}
```

- Permission: `products:update`
- `productId` query param optional — when present, processes only that product
- Returns `ProductImageProcessingResult`

### Response

```json
{
  "status": "success",
  "message": "Processing complete",
  "data": {
    "total": 50,
    "processed": 38,
    "skipped": 10,
    "compressed": 12,
    "failed": 2,
    "errors": [
      "abc-123: headObject failed: NoSuchKey",
      "def-456: compression failed: corrupted image"
    ]
  }
}
```

---

## Code Structure

```
service/
├── StorageService.java                     (modified — +headObject, +getObject)
├── ProductImageProcessingService.java      (new)
└── imaging/                                (unchanged)

controller/
└── AdminProductImageController.java        (new)

dto/admin/
└── ProductImageProcessingResult.java       (new — record)
```

No new database tables. No new migrations.

---

## Error Handling

| Scenario | Behavior |
|----------|----------|
| `product.imageUrl == null` | Count as `skipped`, silent |
| `headObject` fails (404, S3 error) | Count as `failed`, log WARN, continue |
| `getObject` fails | Count as `failed`, log WARN, continue |
| Compression fails (corrupted image) | Count as `failed`, log WARN, continue |
| Compressed upload fails | Count as `failed`, original preserved via temp-key strategy |
| Thumbnail generation/upload fails | Log WARN, that thumbnail omitted, product continues |
| Has thumbnails AND original ≤ 700KB | Count as `skipped` |

**One product failure never blocks the next.** All processing is wrapped in per-product try-catch.

---

## Testing

### StorageServiceTest (additions)
- `headObject` returns sizeBytes and contentType for existing key
- `headObject` with nonexistent key → `NoSuchKeyException`
- `getObject` returns full bytes of stored object

### ProductImageProcessingServiceTest (new)
- Product without `imageUrl` → `skipped`
- Product with original ≤ 700KB AND has thumbnails → `skipped`
- Product with original > 700KB → compressed, thumbnails saved
- Product with original ≤ 700KB without thumbnails → only thumbnails generated
- `headObject` failure → `failed`, continues to next product
- `processAll` with 3 products, 1 fails → 2 succeed, 1 failed, errors list populated

### ProductControllerIntegrationTest (additions)
- `POST /api/admin/products/process-images` without auth → 401 or 403
- `POST /api/admin/products/process-images` with valid auth → 200, result fields present
- `POST /api/admin/products/process-images?productId={id}` → 200, single product processed

---

## Implementation Order

| Step | What | Where |
|------|------|-------|
| 1 | `headObject` + `getObject` methods | `StorageService.java` |
| 2 | `ProductImageProcessingResult` record | `dto/admin/` |
| 3 | `ProductImageProcessingService` | `service/` |
| 4 | `AdminProductImageController` | `controller/` |
| 5 | Unit tests — StorageService | `StorageServiceTest.java` |
| 6 | Unit tests — ProcessingService | `ProductImageProcessingServiceTest.java` |
| 7 | Integration tests | `ProductControllerIntegrationTest.java` |
| 8 | Full suite regression | `./gradlew clean test` |
