# Product Image Thumbnails — Design Spec

**Date:** 2026-06-23
**Status:** Draft
**Decision:** Generate 3 thumbnails server-side at upload time

## Context

- **Storage:** Cloudflare R2 (S3-compatible), no image transformation features
- **Frontend:** Next.js, types written by hand (no generated SDK)
- **Scale:** Hundreds of products per tenant
- **Display contexts:** List/table (small), card/grid (medium), detail view (large)
- **Current state:** Single `image_url` per product, uploaded as-is, no server-side processing

## Goals

1. Frontend loads appropriately-sized images for each display context
2. List views download ~97% less data (small thumbnails vs full originals)
3. Backend change is minimal — no new endpoints, no new infrastructure
4. Existing products without thumbnails continue to work (graceful fallback)

## Non-goals

- Image format conversion beyond JPEG thumbnails (WebP/AVIF deferred)
- Batch migration of existing products (lazy — thumbnails generated on next update)
- Thumbnails for temporary upload flow (`ProductImageUploadService`)

---

## Storage Structure (R2)

```
products/{uuid}{.ext}       → original (extension preserved from upload: .jpg, .png, .webp)
products/{uuid}_sm.jpg      → 150px small (always JPEG)
products/{uuid}_md.jpg      → 400px medium (always JPEG)
products/{uuid}_lg.jpg      → 800px large (always JPEG)
```

The suffix convention (`_sm`, `_md`, `_lg`) is applied to the storage key, so existing `deleteImage()` logic that extracts keys from URLs can derive thumbnail keys without extra lookups. Thumbnails are always JPEG regardless of the original format — the `product_image_thumbnails.content_type` column records the actual MIME type per variant.

## Database

New table `product_image_thumbnails`:

```sql
CREATE TABLE product_image_thumbnails (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    size VARCHAR(10) NOT NULL,       -- 'sm', 'md', 'lg'
    storage_key VARCHAR(500) NOT NULL,
    public_url VARCHAR(500) NOT NULL,
    width_px INTEGER NOT NULL,
    height_px INTEGER,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL DEFAULT 'image/jpeg',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, size)
);
```

`products.image_url` is **unchanged** — it continues to hold the original image URL. This preserves compatibility with all existing queries, DTOs, and external consumers. Thumbnail URLs are loaded via a separate query and merged into `ProductResponse`.

Why a separate table rather than adding columns (`image_url_sm`, etc.) to `products`:
- Adding a new size doesn't require a schema migration
- No nullable columns for products without images
- Clean separation — code that doesn't care about thumbnails doesn't see them

## Processing Pipeline

### Library: Thumbnailator

```groovy
implementation 'net.coobird:thumbnailator:0.4.20'
```

Pure Java, no native dependencies, ~300 KB. Wrapped behind a project-owned interface so the implementation can be swapped.

### Output format

| Variant | Max width | Format | Quality | Est. weight |
|---------|-----------|--------|---------|-------------|
| original | - | as received | - | variable |
| large | 800px | JPEG | 85% | ~60-80 KB |
| medium | 400px | JPEG | 82% | ~20-30 KB |
| small | 150px | JPEG | 80% | ~5-8 KB |

JPEG chosen for V1: predictable quality/weight trade-off, universal browser support. WebP can be added later by changing `content_type` in the thumbnail row — the table tracks format per thumbnail.

### Flow in StorageService

```
MultipartFile
  ├── upload original → products/{uuid}.jpg
  ├── resize → 150px → upload → products/{uuid}_sm.jpg
  ├── resize → 400px → upload → products/{uuid}_md.jpg
  └── resize → 800px → upload → products/{uuid}_lg.jpg
```

## Code Structure

```
service/
├── StorageService.java              (modified — uploadProductImageWithThumbnails, deleteProductImages)
├── imaging/
│   ├── ThumbnailGenerator.java      (new — interface)
│   └── ThumbnailatorGenerator.java  (new — Thumbnailator implementation)
├── ProductService.java              (modified — save/load/delete thumbnails)
└── upload/
    └── ProductImageUploadService.java (UNCHANGED — temp upload flow does not generate thumbnails)

dto/product/
└── ProductResponse.java             (modified — adds thumbnails Map)

model/entity/
└── ProductImageThumbnail.java       (new — composite PK: product_id + size)

repository/
└── ProductImageThumbnailRepository.java  (new)

controller/
└── ProductController.java           (UNCHANGED — response shape changes, signature does not)
```

No new endpoints. No new infrastructure. The public API only changes in the response payload shape.

## API Contract

### ProductResponse (new field)

```java
@Builder.Default
private Map<String, String> thumbnails = Map.of();
// {"sm": "https://...", "md": "https://...", "lg": "https://..."}
```

Products without thumbnails (pre-migration or error) return an empty map. The frontend falls back to `imageUrl`.

## Frontend Integration (Next.js)

Each context picks the right thumbnail:

```tsx
// List — 150px render
<Image src={product.thumbnails.sm || product.imageUrl} width={150} height={150} />

// Card — 400px render
<Image src={product.thumbnails.md || product.imageUrl} width={400} height={400} />

// Detail — 800px max
<Image src={product.thumbnails.lg || product.imageUrl} width={800} height={800} />
```

Bandwidth impact: a 100-product list goes from ~20 MB (originals) to ~600 KB (small thumbnails) — a ~97% reduction.

## Error Handling

- **Original upload fails:** Exception thrown, transaction rolled back, nothing persisted
- **Thumbnail generation fails:** Logged at WARN, that thumbnail is omitted from the result map, the product is still created/updated with whatever thumbnails succeeded
- **Thumbnail upload to R2 fails:** Same as generation failure — omitted, logged, product creation continues
- **Delete: thumbnail not found in R2:** `deleteStorageKeyQuietly` logs WARN and continues — doesn't block the delete operation
- **Concurrent update (two requests editing same product image):** last-write-wins; old thumbnails are replaced by new ones. R2 eventual consistency means old keys may briefly return 404 which is expected
- **Image too small for a requested size:** Thumbnailator handles this gracefully (doesn't upscale beyond original). If the original is 300px, `_lg` (800px) will still be generated at 300px — the frontend's `width` attribute on `<Image>` handles the layout

## Delete & Cleanup

### On product image update
1. Query existing thumbnail rows for the product
2. Delete old original + all thumbnail keys from R2
3. Delete old thumbnail rows from DB
4. Upload new original + generate new thumbnails
5. Insert new thumbnail rows

### On product delete (soft delete)
1. Query thumbnail rows for the product
2. Delete original + all thumbnail keys from R2
3. Rows are cleaned up by `ON DELETE CASCADE` (if product is hard-deleted) or kept (soft delete — they're harmless and would be reused if the product is restored)

### Existing products (no thumbnails)
No migration job in V1. Products without thumbnails return an empty `thumbnails` map. The frontend falls back to `imageUrl`. Thumbnails are generated on the next product update that includes a new image.

## Testing

### StorageServiceTest
- `uploadProductImageWithThumbnails` generates 4 files in bucket (original + sm + md + lg)
- Thumbnails have correct dimensions (150, 400, 800)
- Output format is JPEG with specified quality
- File without extension defaults to `.jpg`
- Original upload failure → exception, no files persisted
- Thumbnail failure → original succeeds, missing thumbnail omitted from result, WARN logged

### ProductServiceTest
- `create` with image → saves product with original `imageUrl`, saves 3 thumbnail rows
- `create` rollback → no thumbnail rows persisted
- `update` with new image → deletes old thumbnails from R2 and DB, saves new ones
- `update` without image → thumbnails preserved
- `delete` → deletes original + thumbnails from R2

### ProductControllerIntegrationTest
- `POST /api/products` (multipart with image) → response includes `thumbnails` with sm, md, lg
- `GET /api/products/{id}` → response includes correct thumbnail URLs

### Regression
- `StockMovementService` temp-upload → promote flow works without thumbnails
- `PUT /api/products/{id}` without image → existing thumbnails preserved
- `DELETE /api/products/{id}` → no orphaned R2 objects, no errors

## Implementation Order

| Step | What | Where |
|------|------|-------|
| 1 | Migration: `product_image_thumbnails` table | `db/migration/Vxx__product_image_thumbnails.sql` |
| 2 | Entity + Repository | `model/entity/ProductImageThumbnail.java`, `repository/` |
| 3 | `ThumbnailGenerator` interface + `ThumbnailatorGenerator` | `service/imaging/` |
| 4 | `StorageService` — upload + delete methods | `service/StorageService.java` |
| 5 | `ProductService` — integrate create/update/delete | `service/ProductService.java` |
| 6 | `ProductResponse` — add `thumbnails` field | `dto/product/ProductResponse.java` |
| 7 | Unit tests | `StorageServiceTest`, `ProductServiceTest` |
| 8 | Integration tests | `ProductControllerIntegrationTest` |
| 9 | Regression test check | `StockMovementServiceTest` |

## Dependencies Added

```
net.coobird:thumbnailator:0.4.20
```
