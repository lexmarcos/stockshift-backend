package br.com.stockshift.service;

import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Brand;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.ProductImageThumbnail;
import br.com.stockshift.model.enums.BarcodeType;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.ProductImageThumbnailRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.audit.AuditService;
import br.com.stockshift.service.audit.AuditSnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private AuditSnapshotService auditSnapshotService;
    @Mock
    private StorageService storageService;
    @Mock
    private ProductImageThumbnailRepository thumbnailRepository;

    private ProductService productService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
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
        when(auditSnapshotService.snapshot(any())).thenReturn(Map.of("id", "value"));
        when(auditSnapshotService.diff(any(), any())).thenReturn(List.of("name"));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            if (product.getId() == null) {
                product.setId(UUID.randomUUID());
            }
            if (product.getCreatedAt() == null) {
                product.setCreatedAt(LocalDateTime.now());
            }
            if (product.getUpdatedAt() == null) {
                product.setUpdatedAt(LocalDateTime.now());
            }
            return product;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createShouldPersistProductWithCategoryBrandImageAndAudit() {
        Category category = category("Bebidas");
        Brand brand = brand("Acme", null);
        MockMultipartFile image = image();
        when(categoryRepository.findByTenantIdAndId(tenantId, category.getId()))
                .thenReturn(Optional.of(category));
        when(brandRepository.findByTenantIdAndId(tenantId, brand.getId()))
                .thenReturn(Optional.of(brand));
        when(storageService.uploadProductImageWithThumbnails(image))
                .thenReturn(thumbnails());
        when(thumbnailRepository.findByProductId(any())).thenReturn(thumbEntities());

        var response = productService.create(fullRequest(category.getId(), brand.getId()), image);

        assertThat(response.getCategoryName()).isEqualTo("Bebidas");
        assertThat(response.getBrand().getName()).isEqualTo("Acme");
        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.com/product.png");
        assertThat(response.getThumbnails()).containsKeys("sm", "md", "lg");
        assertThat(response.getThumbnails().get("sm")).isEqualTo("https://cdn.example.com/product_sm.jpg");
        assertThat(response.getSku()).isEqualTo("SKU-1");
        verify(auditService).record(any());
    }

    @Test
    void createShouldGenerateSkuAndApplyDefaultsWhenOptionalFieldsAreMissing() {
        ProductRequest request = new ProductRequest();
        request.setName("Produto sem SKU");
        request.setDescription("Descrição");
        when(productRepository.findBySkuAndTenantId(any(), eq(tenantId))).thenReturn(Optional.empty());

        Product product = productService.createEntity(request);

        assertThat(product.getSku()).startsWith("PRD-");
        assertThat(product.getIsKit()).isFalse();
        assertThat(product.getHasExpiration()).isFalse();
        assertThat(product.getActive()).isTrue();
        assertThat(product.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void createShouldRejectDuplicateBarcodeAndSku() {
        Product existing = product("Existente");
        ProductRequest duplicateBarcode = fullRequest(null, null);
        when(productRepository.findByBarcodeAndTenantId("789", tenantId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> productService.create(duplicateBarcode))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("barcode 789");

        ProductRequest duplicateSku = fullRequest(null, null);
        duplicateSku.setBarcode(null);
        when(productRepository.findBySkuAndTenantId("SKU-1", tenantId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> productService.create(duplicateSku))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SKU SKU-1");
    }

    @Test
    void createShouldDeleteUploadedImageWhenImagePersistenceFails() {
        ProductRequest request = fullRequest(null, null);
        MockMultipartFile image = image();
        var rollbackThumbs = new StorageService.Thumbnails(
                new StorageService.StoredImageObject("products/rollback.png", "https://cdn.example.com/rollback.png"),
                new StorageService.StoredImageObject("products/rollback_sm.jpg", "https://cdn.example.com/rollback_sm.jpg"),
                null,
                null
        );
        when(storageService.uploadProductImageWithThumbnails(image))
                .thenReturn(rollbackThumbs);
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
                .thenReturn(List.of())
                .thenReturn(thumbEntities());
        when(storageService.uploadProductImageWithThumbnails(image))
                .thenReturn(thumbnails());

        var response = productService.update(product.getId(), request, image);

        assertThat(response.getName()).isEqualTo("Produto novo");
        assertThat(response.getCategoryId()).isNull();
        assertThat(response.getBrand()).isNull();
        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.com/product.png");
        assertThat(response.getThumbnails()).containsKeys("sm", "md", "lg");
        verify(storageService).deleteProductImages(eq("https://cdn.example.com/old.png"), any());
        verify(auditService).record(any());
    }

    @Test
    void updateWithoutImageShouldPreserveExistingThumbnails() {
        Product product = product("Produto com imagem");
        product.setImageUrl("https://cdn.example.com/old.png");
        ProductRequest request = fullRequest(null, null);
        request.setName("Nome atualizado");
        request.setSku("SKU-NEW");
        request.setBarcode("888");
        when(productRepository.findByTenantIdAndId(tenantId, product.getId()))
                .thenReturn(Optional.of(product));
        when(thumbnailRepository.findByProductId(product.getId()))
                .thenReturn(thumbEntities());

        var response = productService.update(product.getId(), request, null);

        assertThat(response.getThumbnails()).isNotEmpty();
        assertThat(response.getThumbnails()).containsKeys("sm", "md", "lg");
        // Verify old image was NOT deleted
        verify(storageService, never()).deleteImage(any());
        verify(storageService, never()).deleteProductImages(any(), any());
    }

    @Test
    void updateShouldRejectMissingCategoryAndDeletedBrand() {
        Product product = product("Produto");
        UUID categoryId = UUID.randomUUID();
        ProductRequest missingCategory = fullRequest(categoryId, null);
        when(productRepository.findByTenantIdAndId(tenantId, product.getId()))
                .thenReturn(Optional.of(product));
        when(categoryRepository.findByTenantIdAndId(tenantId, categoryId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(product.getId(), missingCategory))
                .isInstanceOf(ResourceNotFoundException.class);

        UUID brandId = UUID.randomUUID();
        ProductRequest deletedBrand = fullRequest(null, brandId);
        Brand brand = brand("Inativa", LocalDateTime.now());
        when(brandRepository.findByTenantIdAndId(tenantId, brandId))
                .thenReturn(Optional.of(brand));

        assertThatThrownBy(() -> productService.update(product.getId(), deletedBrand))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findersShouldMapProductsAndMissingProductShouldThrow() {
        Product product = product("Produto");
        product.setCategory(category("Categoria"));
        product.setBrand(brand("Marca", null));
        when(thumbnailRepository.findByProductIdIn(any())).thenReturn(List.of());
        when(productRepository.findAllByTenantId(tenantId)).thenReturn(List.of(product));
        when(productRepository.findByTenantIdAndId(tenantId, product.getId()))
                .thenReturn(Optional.of(product));
        when(productRepository.findByTenantIdAndCategoryId(tenantId, product.getCategory().getId()))
                .thenReturn(List.of(product));
        when(productRepository.findByTenantIdAndActive(tenantId, true)).thenReturn(List.of(product));
        when(productRepository.searchByTenantId(tenantId, "prod")).thenReturn(List.of(product));
        when(productRepository.findByBarcodeAndTenantId("789", tenantId)).thenReturn(Optional.of(product));
        when(productRepository.findBySkuAndTenantId("SKU-1", tenantId)).thenReturn(Optional.of(product));

        assertThat(productService.findAll()).singleElement().extracting("name").isEqualTo("Produto");
        assertThat(productService.findById(product.getId()).getBrand().getName()).isEqualTo("Marca");
        assertThat(productService.findByCategory(product.getCategory().getId())).hasSize(1);
        assertThat(productService.findActive(true)).hasSize(1);
        assertThat(productService.search("prod")).hasSize(1);
        assertThat(productService.findByBarcode("789").getSku()).isEqualTo("SKU-1");
        assertThat(productService.findBySku("SKU-1").getBarcode()).isEqualTo("789");

        assertThatThrownBy(() -> productService.findById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

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

    @Test
    void deleteShouldSoftDeleteProductImageAndRecordAudit() {
        Product product = product("Produto");
        product.setImageUrl("https://cdn.example.com/product.png");
        when(productRepository.findByTenantIdAndId(tenantId, product.getId()))
                .thenReturn(Optional.of(product));
        when(thumbnailRepository.findByProductId(product.getId())).thenReturn(List.of());

        productService.delete(product.getId());

        assertThat(product.getDeletedAt()).isNotNull();
        verify(storageService).deleteProductImages("https://cdn.example.com/product.png", List.of());
        verify(batchRepository).softDeleteByProduct(product.getId(), tenantId);
        verify(productRepository).save(product);
        verify(auditService).record(any());
    }

    @Test
    void recordAuditShouldIncludeProductResourceId() {
        Product product = product("Produto");
        when(productRepository.findByTenantIdAndId(tenantId, product.getId()))
                .thenReturn(Optional.of(product));
        when(thumbnailRepository.findByProductId(product.getId())).thenReturn(List.of());
        ArgumentCaptor<br.com.stockshift.service.audit.AuditEventCreateRequest> captor =
                ArgumentCaptor.forClass(br.com.stockshift.service.audit.AuditEventCreateRequest.class);

        productService.delete(product.getId());

        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().resourceType()).isEqualTo("PRODUCT");
        assertThat(captor.getValue().resourceId()).isEqualTo(product.getId().toString());
    }

    private ProductRequest fullRequest(UUID categoryId, UUID brandId) {
        return ProductRequest.builder()
                .name("Produto")
                .description("Descrição")
                .categoryId(categoryId)
                .brandId(brandId)
                .barcode("789")
                .barcodeType(BarcodeType.EXTERNAL)
                .sku("SKU-1")
                .isKit(true)
                .attributes(Map.of("color", "red"))
                .hasExpiration(true)
                .active(true)
                .imageUrl("https://example.com/product.png")
                .build();
    }

    private Product product(String name) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setTenantId(tenantId);
        product.setName(name);
        product.setDescription("Descrição");
        product.setBarcode("789");
        product.setBarcodeType(BarcodeType.EXTERNAL);
        product.setSku("SKU-1");
        product.setIsKit(false);
        product.setHasExpiration(false);
        product.setActive(true);
        product.setAttributes(Map.of("size", "M"));
        product.setImageUrl("https://example.com/product.png");
        product.setCreatedAt(LocalDateTime.now().minusDays(1));
        product.setUpdatedAt(LocalDateTime.now());
        return product;
    }

    private Category category(String name) {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setTenantId(tenantId);
        category.setName(name);
        return category;
    }

    private Brand brand(String name, LocalDateTime deletedAt) {
        Brand brand = new Brand();
        brand.setId(UUID.randomUUID());
        brand.setTenantId(tenantId);
        brand.setName(name);
        brand.setLogoUrl("https://example.com/logo.png");
        brand.setDeletedAt(deletedAt);
        return brand;
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "product.png", "image/png", new byte[]{1});
    }

    private StorageService.Thumbnails thumbnails() {
        var original = new StorageService.StoredImageObject(
                "products/test.png", "https://cdn.example.com/product.png");
        var small = new StorageService.StoredImageObject(
                "products/test_sm.jpg", "https://cdn.example.com/product_sm.jpg");
        var medium = new StorageService.StoredImageObject(
                "products/test_md.jpg", "https://cdn.example.com/product_md.jpg");
        var large = new StorageService.StoredImageObject(
                "products/test_lg.jpg", "https://cdn.example.com/product_lg.jpg");
        return new StorageService.Thumbnails(original, small, medium, large);
    }

    private List<ProductImageThumbnail> thumbEntities() {
        return List.of(
                ProductImageThumbnail.builder()
                        .size("sm")
                        .publicUrl("https://cdn.example.com/product_sm.jpg")
                        .build(),
                ProductImageThumbnail.builder()
                        .size("md")
                        .publicUrl("https://cdn.example.com/product_md.jpg")
                        .build(),
                ProductImageThumbnail.builder()
                        .size("lg")
                        .publicUrl("https://cdn.example.com/product_lg.jpg")
                        .build()
        );
    }
}
