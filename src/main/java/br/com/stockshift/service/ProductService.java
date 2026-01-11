package br.com.stockshift.service;

import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.dto.product.ProductResponse;
import br.com.stockshift.dto.brand.BrandResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Brand;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final StorageService storageService;

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
        // Upload image if provided
        if (image != null && !image.isEmpty()) {
            String imageUrl = storageService.uploadImage(image);
            request.setImageUrl(imageUrl);
        }

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
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(category);
        product.setBrand(brand);
        product.setBarcode(request.getBarcode());
        product.setBarcodeType(request.getBarcodeType());
        product.setSku(sku);
        product.setIsKit(request.getIsKit() != null ? request.getIsKit() : false);
        product.setAttributes(request.getAttributes());
        product.setHasExpiration(request.getHasExpiration() != null ? request.getHasExpiration() : false);
        product.setActive(request.getActive() != null ? request.getActive() : true);
        product.setImageUrl(request.getImageUrl());

        Product saved = productRepository.save(product);
        log.info("Created product {} for tenant {}", saved.getId(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        return create(request, null);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return productRepository.findAllByTenantId(tenantId).stream()
                .map(this::mapToResponse)
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
        return productRepository.findByTenantIdAndCategoryId(tenantId, categoryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findActive(Boolean active) {
        UUID tenantId = TenantContext.getTenantId();
        return productRepository.findByTenantIdAndActive(tenantId, active).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> search(String searchTerm) {
        UUID tenantId = TenantContext.getTenantId();
        return productRepository.searchByTenantId(tenantId, searchTerm).stream()
                .map(this::mapToResponse)
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

        // Upload new image if provided
        if (image != null && !image.isEmpty()) {
            // Delete old image if exists
            if (product.getImageUrl() != null) {
                storageService.deleteImage(product.getImageUrl());
            }
            String imageUrl = storageService.uploadImage(image);
            product.setImageUrl(imageUrl);
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

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBarcode(request.getBarcode());
        product.setBarcodeType(request.getBarcodeType());
        product.setSku(request.getSku());
        product.setIsKit(request.getIsKit() != null ? request.getIsKit() : false);
        product.setAttributes(request.getAttributes());
        product.setHasExpiration(request.getHasExpiration() != null ? request.getHasExpiration() : false);
        product.setActive(request.getActive() != null ? request.getActive() : true);

        Product updated = productRepository.save(product);
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

        // Delete image if exists
        if (product.getImageUrl() != null) {
            storageService.deleteImage(product.getImageUrl());
        }

        // Soft delete
        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("Soft deleted product {} for tenant {}", id, tenantId);
    }

    private ProductResponse mapToResponse(Product product) {
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
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
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
