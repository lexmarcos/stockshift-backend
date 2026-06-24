package br.com.stockshift.service;

import br.com.stockshift.dto.admin.ProductImageProcessingResult;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.ProductImageThumbnail;
import br.com.stockshift.repository.ProductImageThumbnailRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.imaging.ThumbnailGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@ConditionalOnBean(StorageService.class)
@Slf4j
public class ProductImageProcessingService {

    private static final long MAX_ORIGINAL_BYTES = 700 * 1024; // 700 KB
    private static final float COMPRESSION_QUALITY = 0.80f;
    private static final int[] THUMBNAIL_WIDTHS = {150, 400, 800};
    private static final String[] THUMBNAIL_SUFFIXES = {"_sm", "_md", "_lg"};
    private static final float[] THUMBNAIL_QUALITIES = {0.80f, 0.82f, 0.85f};

    private final ProductRepository productRepository;
    private final ProductImageThumbnailRepository thumbnailRepository;
    @Autowired(required = false)
    @Nullable
    private StorageService storageService;
    private final ThumbnailGenerator thumbnailGenerator;
    private final TransactionTemplate transactionTemplate;

    public ProductImageProcessingService(
            ProductRepository productRepository,
            ProductImageThumbnailRepository thumbnailRepository,
            @Nullable StorageService storageService,
            ThumbnailGenerator thumbnailGenerator,
            TransactionTemplate transactionTemplate) {
        this.productRepository = productRepository;
        this.thumbnailRepository = thumbnailRepository;
        this.storageService = storageService;
        this.thumbnailGenerator = thumbnailGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    public ProductImageProcessingResult processAll() {
        UUID tenantId = TenantContext.getTenantId();
        List<Product> products = productRepository.findAllByTenantId(tenantId);
        return processProducts(products);
    }

    public ProductImageProcessingResult processOne(UUID productId) {
        UUID tenantId = TenantContext.getTenantId();
        Product product = productRepository.findByTenantIdAndId(tenantId, productId)
                .orElse(null);
        if (product == null) {
            return new ProductImageProcessingResult(0, 0, 0, 0, 0,
                    List.of("Product not found: " + productId));
        }
        return processProducts(List.of(product));
    }

    public ProductImageProcessingResult processProduct(Product product) {
        return processProducts(List.of(product));
    }

    private ProductImageProcessingResult processProducts(List<Product> products) {
        int total = 0;
        int processed = 0;
        int skipped = 0;
        int compressed = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (Product product : products) {
            if (product.getImageUrl() == null) {
                continue;
            }
            total++;

            try {
                ProcessOneResult result = processOneProduct(product);
                switch (result) {
                    case SKIPPED -> skipped++;
                    case PROCESSED -> processed++;
                    case COMPRESSED -> { processed++; compressed++; }
                }
            } catch (Exception e) {
                log.warn("Failed to process image for product {}: {}", product.getId(), e.getMessage());
                failed++;
                errors.add(product.getId() + ": " + e.getMessage());
            }
        }

        return new ProductImageProcessingResult(total, processed, skipped, compressed, failed, errors);
    }

    private enum ProcessOneResult { SKIPPED, PROCESSED, COMPRESSED }

    private ProcessOneResult processOneProduct(Product product) {
        String storageKey = extractKeyFromUrl(product.getImageUrl());
        byte[] imageBytes;

        // Check if already good
        List<ProductImageThumbnail> existingThumbnails =
                thumbnailRepository.findByProductId(product.getId());
        boolean hasFullThumbnailSet = existingThumbnails.size() == THUMBNAIL_SUFFIXES.length
                && existingThumbnails.stream()
                        .map(ProductImageThumbnail::getSize)
                        .collect(Collectors.toSet())
                        .containsAll(Set.of("sm", "md", "lg"));

        try {
            StorageService.HeadObjectResult head = storageService.headObject(storageKey);
            if (hasFullThumbnailSet && head.sizeBytes() <= MAX_ORIGINAL_BYTES) {
                log.debug("Product {} already has thumbnails and original <= 700KB, skipping",
                        product.getId());
                return ProcessOneResult.SKIPPED;
            }
        } catch (NoSuchKeyException e) {
            log.warn("Original image not found for product {}: {}", product.getId(), storageKey);
            return ProcessOneResult.SKIPPED;
        }

        // Download
        imageBytes = storageService.getObject(storageKey);

        // Compress if needed
        boolean wasCompressed = false;
        if (imageBytes.length > MAX_ORIGINAL_BYTES) {
            imageBytes = compressImage(imageBytes);
            wasCompressed = true;
            // Safe overwrite: upload to temp, copy to original, delete temp
            String tempKey = storageKey + ".tmp";
            uploadBytes(tempKey, imageBytes, "image/jpeg");
            storageService.copyObject(tempKey, storageKey);
            storageService.deleteStorageKeyQuietly(tempKey);
        }

        // Generate replacements BEFORE retiring the existing thumbnails (PR #5 review).
        // Deleting first would discard working thumbnails if generation/upload then failed,
        // while processProducts still counted the product as processed.
        List<ProductImageThumbnail> replacements =
                buildThumbnails(product.getId(), imageBytes, storageKey);
        if (replacements.size() != THUMBNAIL_SUFFIXES.length) {
            // A per-size transient failure inside buildThumbnails would produce a
            // partial set; retiring the old rows would turn a single-size failure into
            // permanently missing thumbnail URLs (PR #5 review). Require all three.
            // Clean up the partial R2 uploads so they aren't orphaned.
            replacements.forEach(
                    t -> storageService.deleteStorageKeyQuietly(t.getStorageKey()));
            throw new IllegalStateException(
                    "Thumbnail generation incomplete for product " + product.getId()
                    + ": expected " + THUMBNAIL_SUFFIXES.length
                    + " sizes, got " + replacements.size());
        }
        // Run the DB row swap in its own transaction so a saveAll failure does
        // not leave the deleteAll auto-committed (PR #5 review). The obsolete-object
        // cleanup stays outside the transaction: it targets different keys than the
        // replacements just uploaded, so a stale-object delete after a rollback is
        // harmless, while rolling back R2 deletes is impossible.
        transactionTemplate.executeWithoutResult(
                status -> replaceThumbnailRows(existingThumbnails, replacements));
        deleteObsoleteThumbnailObjects(existingThumbnails, replacements);

        return wasCompressed ? ProcessOneResult.COMPRESSED : ProcessOneResult.PROCESSED;
    }

    private byte[] compressImage(byte[] original) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
            if (image == null) {
                throw new IllegalStateException("Failed to decode image for compression");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            net.coobird.thumbnailator.Thumbnails.of(image)
                    .scale(1.0)
                    .outputFormat("jpg")
                    .outputQuality(COMPRESSION_QUALITY)
                    .toOutputStream(out);
            byte[] compressed = out.toByteArray();
            log.debug("Compressed image from {} bytes to {} bytes", original.length, compressed.length);
            return compressed;
        } catch (Exception e) {
            throw new RuntimeException("Failed to compress image", e);
        }
    }

    private void uploadBytes(String key, byte[] bytes, String contentType) {
        storageService.uploadBytes(key, bytes, contentType);
    }

    /**
     * Swaps the persisted thumbnail rows for a product. The old rows are deleted and flushed
     * before the replacements are inserted because both share the composite (product_id, size)
     * primary key, and Hibernate would otherwise order the INSERTs before the DELETEs.
     */
    private void replaceThumbnailRows(
            List<ProductImageThumbnail> oldRows, List<ProductImageThumbnail> replacements) {
        if (!oldRows.isEmpty()) {
            thumbnailRepository.deleteAll(oldRows);
            thumbnailRepository.flush();
        }
        thumbnailRepository.saveAll(replacements);
    }

    /**
     * Deletes old storage objects that the replacements did not overwrite. Thumbnail keys are
     * derived deterministically from the original key, so a reprocess usually reuses the same
     * keys (nothing to delete); this only removes genuinely stale objects.
     */
    private void deleteObsoleteThumbnailObjects(
            List<ProductImageThumbnail> oldRows, List<ProductImageThumbnail> replacements) {
        Set<String> retainedKeys = replacements.stream()
                .map(ProductImageThumbnail::getStorageKey)
                .collect(Collectors.toSet());
        for (ProductImageThumbnail old : oldRows) {
            if (!retainedKeys.contains(old.getStorageKey())) {
                storageService.deleteStorageKeyQuietly(old.getStorageKey());
            }
        }
    }

    private List<ProductImageThumbnail> buildThumbnails(UUID productId, byte[] sourceBytes, String originalKey) {
        List<ProductImageThumbnail> entities = new ArrayList<>();
        for (int i = 0; i < THUMBNAIL_WIDTHS.length; i++) {
            try {
                ThumbnailGenerator.ThumbnailSpec spec = new ThumbnailGenerator.ThumbnailSpec(
                        THUMBNAIL_WIDTHS[i], THUMBNAIL_QUALITIES[i]);

                ThumbnailGenerator.ThumbnailResult result = thumbnailGenerator.generate(
                        new ByteArrayInputStream(sourceBytes), "image/jpeg", "source", spec);

                byte[] thumbBytes;
                try (InputStream is = result.inputStream()) {
                    thumbBytes = is.readAllBytes();
                }

                String thumbKey = deriveThumbnailKey(originalKey, THUMBNAIL_SUFFIXES[i]);
                String thumbUrl = buildPublicUrl(thumbKey);
                uploadBytes(thumbKey, thumbBytes, "image/jpeg");

                entities.add(ProductImageThumbnail.builder()
                        .productId(productId)
                        .size(sizeFromIndex(i))
                        .storageKey(thumbKey)
                        .publicUrl(thumbUrl)
                        .widthPx(THUMBNAIL_WIDTHS[i])
                        .heightPx(result.heightPx() > 0 ? result.heightPx() : null)
                        .sizeBytes((long) thumbBytes.length)
                        .contentType("image/jpeg")
                        .createdAt(java.time.LocalDateTime.now())
                        .build());
            } catch (Exception e) {
                log.warn("Failed to generate thumbnail {} for product {}: {}",
                        THUMBNAIL_SUFFIXES[i], productId, e.getMessage());
            }
        }
        return entities;
    }

    private String sizeFromIndex(int i) {
        return switch (i) {
            case 0 -> "sm";
            case 1 -> "md";
            case 2 -> "lg";
            default -> throw new IllegalArgumentException("Invalid thumbnail index: " + i);
        };
    }

    private String deriveThumbnailKey(String originalKey, String suffix) {
        int dotIndex = originalKey.lastIndexOf('.');
        if (dotIndex > 0) {
            return originalKey.substring(0, dotIndex) + suffix + ".jpg";
        }
        return originalKey + suffix + ".jpg";
    }

    private String buildPublicUrl(String key) {
        // Relies on StorageService's internal buildPublicUrl being accessible.
        // Since it's private in StorageService, we duplicate the logic here:
        return storageService.getPublicUrl() + "/" + key;
    }

    private String extractKeyFromUrl(String imageUrl) {
        // Same as StorageService.extractKeyFromUrl but duplicated to avoid
        // exposing private method.
        String publicUrl = storageService.getPublicUrl();
        if (imageUrl.startsWith(publicUrl)) {
            return imageUrl.substring(publicUrl.length() + 1);
        }
        throw new IllegalArgumentException("Invalid image URL: " + imageUrl);
    }
}
