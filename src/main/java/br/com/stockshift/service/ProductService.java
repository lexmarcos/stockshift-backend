package br.com.stockshift.service;

import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.dto.product.ProductResponse;
import br.com.stockshift.dto.brand.BrandResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Brand;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.ProductImageThumbnail;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.ProductImageThumbnailRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.audit.AuditEventCreateRequest;
import br.com.stockshift.service.audit.AuditService;
import br.com.stockshift.service.audit.AuditSnapshotService;
import br.com.stockshift.util.SanitizationUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final BatchRepository batchRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final AuditService auditService;
    private final AuditSnapshotService auditSnapshotService;
    private final ProductImageThumbnailRepository thumbnailRepository;
    @Autowired(required = false)
    @Nullable
    private StorageService storageService;

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

    /**
     * Generates a unique SKU for a product
     * Format: PRD-{timestamp}-{random}
     */
    private String generateUniqueSku() {
        UUID tenantId = TenantContext.getTenantId();
        String sku;
        int attempts = 0;
        final int maxAttempts = 10;

        do {
            // Generate SKU with format: PRD-{timestamp}-{random}
            long timestamp = System.currentTimeMillis();
            int random = (int) (Math.random() * 1000);
            sku = String.format("PRD-%d-%03d", timestamp, random);

            attempts++;
            if (attempts >= maxAttempts) {
                throw new BusinessException("Failed to generate unique SKU after " + maxAttempts + " attempts");
            }
        } while (productRepository.findBySkuAndTenantId(sku, tenantId).isPresent());

        return sku;
    }

    @Transactional
    public ProductResponse create(ProductRequest request, MultipartFile image) {
        return mapToResponse(createEntity(request, image));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        return create(request, null);
    }

    @Transactional
    public Product createEntity(ProductRequest request) {
        return createProductEntity(request);
    }

    @Transactional
    public Product createEntity(ProductRequest request, MultipartFile image) {
        Product product = createProductEntity(request);
        return attachProductImage(product, image);
    }

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
                .heightPx(stored.heightPx() > 0 ? stored.heightPx() : null)
                .sizeBytes(stored.sizeBytes())
                .contentType("image/jpeg")
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    private Product createProductEntity(ProductRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Validate unique barcode if provided
        if (request.getBarcode() != null) {
            productRepository.findByBarcodeAndTenantId(request.getBarcode(), tenantId)
                    .ifPresent(p -> {
                        throw new BusinessException("Product with barcode " + request.getBarcode() + " already exists");
                    });
        }

        // Generate SKU automatically if not provided
        final String sku;
        if (request.getSku() == null || request.getSku().trim().isEmpty()) {
            sku = generateUniqueSku();
            log.info("Generated automatic SKU: {} for product: {}", sku, request.getName());
        } else {
            sku = request.getSku();
            // Validate unique SKU if provided
            productRepository.findBySkuAndTenantId(sku, tenantId)
                    .ifPresent(p -> {
                        throw new BusinessException("Product with SKU " + sku + " already exists");
                    });
        }

        // Validate category if provided
        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findByTenantIdAndId(tenantId, request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
        }

        // Validate and set brand if provided
        Brand brand = null;
        if (request.getBrandId() != null) {
            brand = brandRepository.findByTenantIdAndId(tenantId, request.getBrandId())
                    .filter(b -> b.getDeletedAt() == null)
                    .orElseThrow(() -> new ResourceNotFoundException("Brand", "id", request.getBrandId()));
        }

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setName(SanitizationUtil.sanitizeForHtml(request.getName()));
        product.setDescription(SanitizationUtil.sanitizeForHtml(request.getDescription()));
        product.setCategory(category);
        product.setBrand(brand);
        product.setBarcode(request.getBarcode());
        product.setBarcodeType(request.getBarcodeType());
        product.setSku(sku);
        product.setIsKit(request.getIsKit() != null ? request.getIsKit() : false);
        product.setAttributes(request.getAttributes());
        product.setHasExpiration(request.getHasExpiration() != null ? request.getHasExpiration() : false);
        product.setActive(request.getActive() != null ? request.getActive() : true);
        product.setImageUrl(SanitizationUtil.sanitizeUrl(request.getImageUrl()));

        Product saved = productRepository.save(product);
        recordProductAudit("PRODUCT_CREATED", null, auditSnapshotService.snapshot(saved), saved.getId());
        log.info("Created product {} for tenant {}", saved.getId(), tenantId);

        return saved;
    }

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

    public void deleteUploadedImageQuietly(String imageUrl) {
        if (imageUrl == null || storageService == null) {
            return;
        }

        try {
            storageService.deleteImage(imageUrl);
        } catch (RuntimeException exception) {
            log.warn("Failed to delete uploaded image after stock movement rollback: {}", imageUrl, exception);
        }
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        List<Product> products = productRepository.findAllByTenantId(tenantId);
        Map<UUID, Map<String, String>> thumbnailMaps = buildThumbnailMapForProducts(products);
        return products.stream()
                .map(p -> mapToResponse(p, thumbnailMaps.getOrDefault(p.getId(), Map.of())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Product product = productRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findByCategory(UUID categoryId) {
        UUID tenantId = TenantContext.getTenantId();
        List<Product> products = productRepository.findByTenantIdAndCategoryId(tenantId, categoryId);
        Map<UUID, Map<String, String>> thumbnailMaps = buildThumbnailMapForProducts(products);
        return products.stream()
                .map(p -> mapToResponse(p, thumbnailMaps.getOrDefault(p.getId(), Map.of())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findActive(Boolean active) {
        UUID tenantId = TenantContext.getTenantId();
        List<Product> products = productRepository.findByTenantIdAndActive(tenantId, active);
        Map<UUID, Map<String, String>> thumbnailMaps = buildThumbnailMapForProducts(products);
        return products.stream()
                .map(p -> mapToResponse(p, thumbnailMaps.getOrDefault(p.getId(), Map.of())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> search(String searchTerm) {
        UUID tenantId = TenantContext.getTenantId();
        List<Product> products = productRepository.searchByTenantId(tenantId, searchTerm);
        Map<UUID, Map<String, String>> thumbnailMaps = buildThumbnailMapForProducts(products);
        return products.stream()
                .map(p -> mapToResponse(p, thumbnailMaps.getOrDefault(p.getId(), Map.of())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse findByBarcode(String barcode) {
        UUID tenantId = TenantContext.getTenantId();
        Product product = productRepository.findByBarcodeAndTenantId(barcode, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "barcode", barcode));
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse findBySku(String sku) {
        UUID tenantId = TenantContext.getTenantId();
        Product product = productRepository.findBySkuAndTenantId(sku, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku", sku));
        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request, MultipartFile image) {
        UUID tenantId = TenantContext.getTenantId();

        Product product = productRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        var before = auditSnapshotService.snapshot(product);

        // Upload new image if provided
        StorageService.Thumbnails newThumbnails = null;
        if (image != null && !image.isEmpty() && storageService != null) {
            // Delete old image and thumbnails if exists
            deleteProductImages(product);
            newThumbnails = storageService.uploadProductImageWithThumbnails(image);
            product.setImageUrl(SanitizationUtil.sanitizeUrl(newThumbnails.original().publicUrl()));
        }

        // Validate unique barcode if changed
        if (request.getBarcode() != null && !request.getBarcode().equals(product.getBarcode())) {
            productRepository.findByBarcodeAndTenantId(request.getBarcode(), tenantId)
                    .ifPresent(p -> {
                        throw new BusinessException("Product with barcode " + request.getBarcode() + " already exists");
                    });
        }

        // Validate unique SKU if changed
        if (request.getSku() != null && !request.getSku().equals(product.getSku())) {
            productRepository.findBySkuAndTenantId(request.getSku(), tenantId)
                    .ifPresent(p -> {
                        throw new BusinessException("Product with SKU " + request.getSku() + " already exists");
                    });
        }

        // Validate category if provided
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findByTenantIdAndId(tenantId, request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }

        // Validate and set brand if provided
        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findByTenantIdAndId(tenantId, request.getBrandId())
                    .filter(b -> b.getDeletedAt() == null)
                    .orElseThrow(() -> new ResourceNotFoundException("Brand", "id", request.getBrandId()));
            product.setBrand(brand);
        } else {
            product.setBrand(null);
        }

        product.setName(SanitizationUtil.sanitizeForHtml(request.getName()));
        product.setDescription(SanitizationUtil.sanitizeForHtml(request.getDescription()));
        product.setBarcode(request.getBarcode());
        product.setBarcodeType(request.getBarcodeType());
        product.setSku(request.getSku());
        product.setIsKit(request.getIsKit() != null ? request.getIsKit() : false);
        product.setAttributes(request.getAttributes());
        product.setHasExpiration(request.getHasExpiration() != null ? request.getHasExpiration() : false);
        product.setActive(request.getActive() != null ? request.getActive() : true);

        Product updated = productRepository.save(product);
        if (newThumbnails != null) {
            saveThumbnails(updated.getId(), newThumbnails);
        }
        var after = auditSnapshotService.snapshot(updated);
        recordProductAudit("PRODUCT_UPDATED", before, after, updated.getId());
        log.info("Updated product {} for tenant {}", id, tenantId);

        return mapToResponse(updated);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        return update(id, request, null);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Product product = productRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        var before = auditSnapshotService.snapshot(product);

        // Delete image and thumbnails if exists
        deleteProductImages(product);

        int deletedBatches = batchRepository.softDeleteByProduct(id, tenantId);
        product.setDeletedAt(LocalDateTime.now());
        Product deleted = productRepository.save(product);
        var after = auditSnapshotService.snapshot(deleted);
        recordProductAudit("PRODUCT_DELETED", before, after, id);

        log.info("Soft deleted product {} and {} batches for tenant {}", id, deletedBatches, tenantId);
    }

    private void recordProductAudit(
            String action,
            java.util.Map<String, Object> before,
            java.util.Map<String, Object> after,
            UUID productId) {
        auditService.record(AuditEventCreateRequest.builder()
                .operation(AuditService.OPERATION_TECHNICAL)
                .action(action)
                .outcome(AuditService.OUTCOME_SUCCESS)
                .resourceType("PRODUCT")
                .resourceId(productId.toString())
                .beforeState(before)
                .afterState(after)
                .changedFields(auditSnapshotService.diff(before, after))
                .build());
    }

    private ProductResponse mapToResponse(Product product) {
        return mapToResponse(product, buildThumbnailMap(product.getId()));
    }

    private ProductResponse mapToResponse(Product product, Map<String, String> thumbnailMap) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .brand(product.getBrand() != null ? mapBrandToResponse(product.getBrand()) : null)
                .barcode(product.getBarcode())
                .barcodeType(product.getBarcodeType())
                .sku(product.getSku())
                .isKit(product.getIsKit())
                .attributes(product.getAttributes())
                .hasExpiration(product.getHasExpiration())
                .active(product.getActive())
                .imageUrl(product.getImageUrl())
                .thumbnails(thumbnailMap)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private Map<String, String> buildThumbnailMap(UUID productId) {
        List<ProductImageThumbnail> thumbnails = thumbnailRepository.findByProductId(productId);
        if (thumbnails.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new HashMap<>();
        for (ProductImageThumbnail t : thumbnails) {
            map.put(t.getSize(), t.getPublicUrl());
        }
        return map;
    }

    private Map<UUID, Map<String, String>> buildThumbnailMapForProducts(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<UUID> productIds = products.stream()
                .map(Product::getId)
                .collect(Collectors.toList());
        List<ProductImageThumbnail> allThumbnails = thumbnailRepository.findByProductIdIn(productIds);

        Map<UUID, Map<String, String>> result = new HashMap<>();
        for (ProductImageThumbnail t : allThumbnails) {
            result.computeIfAbsent(t.getProductId(), k -> new HashMap<>())
                    .put(t.getSize(), t.getPublicUrl());
        }
        // Ensure every product has at least an empty map
        for (Product product : products) {
            result.putIfAbsent(product.getId(), Map.of());
        }
        return result;
    }

    private BrandResponse mapBrandToResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .logoUrl(brand.getLogoUrl())
                .createdAt(brand.getCreatedAt())
                .updatedAt(brand.getUpdatedAt())
                .build();
    }
}
