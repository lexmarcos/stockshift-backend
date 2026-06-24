package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.model.entity.Brand;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.model.enums.BarcodeType;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.PermissionRepository;
import br.com.stockshift.repository.RoleRepository;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import br.com.stockshift.dto.ai.ProductClassificationResponse;
import br.com.stockshift.security.JwtTokenProvider;
import br.com.stockshift.service.OpenAiService;
import br.com.stockshift.service.StorageService;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ProductControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private OpenAiService openAiService;

    @MockitoBean
    private StorageService storageService;

    private Tenant testTenant;
    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUpTestData() {
        // Clear any existing data
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        // Create test tenant
        testTenant = new Tenant();
        testTenant.setBusinessName("Test Tenant");
        testTenant.setDocument("12345678000190");
        testTenant.setEmail("test@tenant.com");
        testTenant.setIsActive(true);
        testTenant = tenantRepository.save(testTenant);

        // Create test user
        testUser = new User();
        testUser.setTenantId(testTenant.getId());
        testUser.setEmail("test@example.com");
        testUser.setPassword(passwordEncoder.encode("password"));
        testUser.setFullName("Test User");
        testUser.setIsActive(true);
        testUser = userRepository.save(testUser);

        // Create ADMIN role and assign to test user
        Permission updatePermission = permissionRepository.findByCodeIgnoreCase("products:update")
                .orElseThrow(() -> new IllegalStateException("products:update permission not found"));

        Role adminRole = new Role();
        adminRole.setTenantId(testTenant.getId());
        adminRole.setName("ADMIN");
        adminRole.setDescription("Admin role for integration tests");
        adminRole.setPermissions(new HashSet<>(Set.of(updatePermission)));
        adminRole = roleRepository.save(adminRole);

        testUser.getRoles().add(adminRole);
        userRepository.save(testUser);

        // Set tenant context for current test
        TenantContext.setTenantId(testTenant.getId());

        // Create test category
        testCategory = new Category();
        testCategory.setTenantId(testTenant.getId());
        testCategory.setName("Test Category");
        testCategory = categoryRepository.save(testCategory);
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void shouldCreateProduct() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Test Product")
                .description("Test Description")
                .categoryId(testCategory.getId())
                .barcode("1234567890")
                .barcodeType(BarcodeType.EXTERNAL)
                .sku("TEST-SKU-001")
                .isKit(false)
                .hasExpiration(false)
                .active(true)
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request));

        mockMvc.perform(multipart("/api/products")
                .file(productPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Test Product"))
                .andExpect(jsonPath("$.data.barcode").value("1234567890"))
                .andExpect(jsonPath("$.data.sku").value("TEST-SKU-001"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void shouldGetProductById() throws Exception {
        Product product = createTestProduct("Get Test Product", "BARCODE-001", "SKU-001");

        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Get Test Product"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void shouldFindProductByBarcode() throws Exception {
        createTestProduct("Barcode Test", "BARCODE-FIND", "SKU-002");

        mockMvc.perform(get("/api/products/barcode/{barcode}", "BARCODE-FIND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.barcode").value("BARCODE-FIND"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void shouldUpdateProduct() throws Exception {
        Product product = createTestProduct("Update Test", "BARCODE-UPDATE", "SKU-UPDATE");

        ProductRequest updateRequest = ProductRequest.builder()
                .name("Updated Product Name")
                .description("Updated Description")
                .categoryId(testCategory.getId())
                .barcode("BARCODE-UPDATE")
                .barcodeType(BarcodeType.EXTERNAL)
                .sku("SKU-UPDATE")
                .isKit(false)
                .hasExpiration(false)
                .active(false)
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(updateRequest));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/products/{id}", product.getId())
                .file(productPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Product Name"))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void shouldDeleteProduct() throws Exception {
        Product product = createTestProduct("Delete Test", "BARCODE-DELETE", "SKU-DELETE");

        mockMvc.perform(delete("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify soft delete
        Product deletedProduct = productRepository.findById(product.getId()).orElseThrow();
        assert deletedProduct.getDeletedAt() != null;
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void shouldSoftDeleteProductBatchesAndAllowSkuReuse() throws Exception {
        Product product = createTestProduct("Reusable Product", "BARCODE-REUSE", "SKU-REUSE");
        Warehouse warehouse = TestDataFactory.createWarehouse(
                warehouseRepository,
                testTenant.getId(),
                "Reusable Warehouse");
        TestDataFactory.createBatch(
                batchRepository,
                testTenant.getId(),
                product,
                warehouse,
                "BATCH-REUSE-PRODUCT",
                10);

        mockMvc.perform(delete("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Product deletedProduct = productRepository.findById(product.getId()).orElseThrow();
        assert deletedProduct.getDeletedAt() != null;
        assert batchRepository.findByProductIdAndTenantId(product.getId(), testTenant.getId()).isEmpty();

        TenantContext.setTenantId(testTenant.getId());

        ProductRequest request = ProductRequest.builder()
                .name("Reusable Product Again")
                .categoryId(testCategory.getId())
                .barcode("BARCODE-REUSE")
                .barcodeType(BarcodeType.EXTERNAL)
                .sku("SKU-REUSE")
                .isKit(false)
                .hasExpiration(false)
                .active(true)
                .build();
        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request));

        mockMvc.perform(multipart("/api/products")
                .file(productPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sku").value("SKU-REUSE"))
                .andExpect(jsonPath("$.data.barcode").value("BARCODE-REUSE"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void shouldSearchProducts() throws Exception {
        createTestProduct("Searchable Product 1", "SEARCH-001", "SEARCH-SKU-001");
        createTestProduct("Searchable Product 2", "SEARCH-002", "SEARCH-SKU-002");

        mockMvc.perform(get("/api/products/search")
                .param("q", "Searchable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void shouldCreateProductWithBrand() throws Exception {
        Brand testBrand = TestDataFactory.createBrand(brandRepository, testTenant.getId(), "Test Brand");

        ProductRequest request = ProductRequest.builder()
                .name("Branded Product")
                .description("Product with brand")
                .categoryId(testCategory.getId())
                .brandId(testBrand.getId())
                .barcode("BRAND-BARCODE-001")
                .barcodeType(BarcodeType.EXTERNAL)
                .sku("BRAND-SKU-001")
                .isKit(false)
                .hasExpiration(false)
                .active(true)
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request));

        mockMvc.perform(multipart("/api/products")
                .file(productPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Branded Product"))
                .andExpect(jsonPath("$.data.brand.id").value(testBrand.getId().toString()))
                .andExpect(jsonPath("$.data.brand.name").value("Test Brand"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void shouldCreateProductWithoutBrand() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Product Without Brand")
                .categoryId(testCategory.getId())
                .barcode("NO-BRAND-BARCODE")
                .barcodeType(BarcodeType.EXTERNAL)
                .sku("NO-BRAND-SKU")
                .isKit(false)
                .hasExpiration(false)
                .active(true)
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request));

        mockMvc.perform(multipart("/api/products")
                .file(productPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Product Without Brand"))
                .andExpect(jsonPath("$.data.brand").isEmpty());
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void shouldNotCreateProductWithInvalidBrand() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Product With Invalid Brand")
                .categoryId(testCategory.getId())
                .brandId(java.util.UUID.randomUUID())
                .barcode("INVALID-BRAND-BARCODE")
                .barcodeType(BarcodeType.EXTERNAL)
                .sku("INVALID-BRAND-SKU")
                .isKit(false)
                .hasExpiration(false)
                .active(true)
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request));

        mockMvc.perform(multipart("/api/products")
                .file(productPart))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void shouldCreateProductWithAutoGeneratedSku() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Product With Auto SKU")
                .description("Test auto-generated SKU")
                .categoryId(testCategory.getId())
                .barcode("AUTO-SKU-BARCODE")
                .barcodeType(BarcodeType.EXTERNAL)
                .isKit(false)
                .hasExpiration(false)
                .active(true)
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request));

        mockMvc.perform(multipart("/api/products")
                .file(productPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Product With Auto SKU"))
                .andExpect(jsonPath("$.data.sku").exists())
                .andExpect(jsonPath("$.data.sku").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void shouldAnalyzeImage() throws Exception {
        ProductClassificationResponse mockResponse = ProductClassificationResponse.builder()
                .name("Analyzed Product")
                .detectedBrand("Analyzed Brand")
                .detectedCategory("Analyzed Category")
                .build();

        when(openAiService.analyzeImage(any())).thenReturn(mockResponse);

        MockMultipartFile imagePart = new MockMultipartFile(
                "image",
                "test-image.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-image-content".getBytes());

        mockMvc.perform(multipart("/api/products/analyze-image")
                .file(imagePart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Analyzed Product"))
                .andExpect(jsonPath("$.data.detectedBrand").value("Analyzed Brand"))
                .andExpect(jsonPath("$.data.detectedCategory").value("Analyzed Category"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void createProductWithImageShouldReturnThumbnailUrls() throws Exception {
        StorageService.Thumbnails thumbnails = new StorageService.Thumbnails(
                new StorageService.StoredImageObject("key_original", "https://cdn.test/product.png"),
                new StorageService.StoredImageObject("key_sm", "https://cdn.test/product_sm.jpg"),
                new StorageService.StoredImageObject("key_md", "https://cdn.test/product_md.jpg"),
                new StorageService.StoredImageObject("key_lg", "https://cdn.test/product_lg.jpg"));

        when(storageService.uploadProductImageWithThumbnails(any())).thenReturn(thumbnails);

        MockMultipartFile image = new MockMultipartFile(
                "image", "product.png", MediaType.IMAGE_PNG_VALUE, new byte[100]);

        ProductRequest request = ProductRequest.builder()
                .name("Product With Thumbnails")
                .categoryId(testCategory.getId())
                .barcode("THUMB-BARCODE-001")
                .barcodeType(BarcodeType.EXTERNAL)
                .sku("THUMB-SKU-001")
                .isKit(false)
                .hasExpiration(false)
                .active(true)
                .build();

        MockMultipartFile productPart = new MockMultipartFile(
                "product",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request));

        mockMvc.perform(multipart("/api/products")
                .file(image)
                .file(productPart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Product With Thumbnails"))
                .andExpect(jsonPath("$.data.thumbnails.sm").exists())
                .andExpect(jsonPath("$.data.thumbnails.md").exists())
                .andExpect(jsonPath("$.data.thumbnails.lg").exists());
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void getProductWithoutImageShouldReturnEmptyThumbnails() throws Exception {
        Product product = createTestProduct("No Image Product", "NO-IMG-BARCODE", "NO-IMG-SKU");

        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.thumbnails").isEmpty());
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void updateProductWithoutImageShouldPreserveExistingThumbnails() throws Exception {
        var product = createTestProduct("Preserve Product", "PRESERVE-BARCODE", "PRESERVE-SKU");
        product.setImageUrl("https://cdn.example.com/existing.png");
        productRepository.save(product);

        ProductRequest updateRequest = ProductRequest.builder()
                .name("Preserve Updated")
                .description(null)
                .barcode("PRESERVE-BARCODE")
                .barcodeType(BarcodeType.EXTERNAL)
                .sku("PRESERVE-SKU")
                .isKit(false)
                .hasExpiration(false)
                .active(true)
                .build();

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/products/" + product.getId())
                .file(new MockMultipartFile("product", "", MediaType.APPLICATION_JSON_VALUE,
                        objectMapper.writeValueAsBytes(updateRequest))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/existing.png"));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "ADMIN" })
    void failedUpdateShouldNotDeleteOldImageFromStorage() throws Exception {
        // PR #5 review: a rolled-back update must not physically delete the old
        // image/thumbnails from storage, or the restored DB rows point at missing objects.
        var target = createTestProduct("Old Image Product", "OLD-IMG-BARCODE", "OLD-IMG-SKU");
        target.setImageUrl("https://cdn.example.com/old.png");
        productRepository.save(target);

        // Second product owns the SKU we collide with to force a post-image-swap failure.
        createTestProduct("Sku Owner", "OWNER-BARCODE", "TAKEN-SKU");

        StorageService.Thumbnails newThumbnails = new StorageService.Thumbnails(
                new StorageService.StoredImageObject("new_original", "https://cdn.test/new.png"),
                new StorageService.StoredImageObject("new_sm", "https://cdn.test/new_sm.jpg"),
                new StorageService.StoredImageObject("new_md", "https://cdn.test/new_md.jpg"),
                new StorageService.StoredImageObject("new_lg", "https://cdn.test/new_lg.jpg"));
        when(storageService.uploadProductImageWithThumbnails(any())).thenReturn(newThumbnails);

        ProductRequest updateRequest = ProductRequest.builder()
                .name("Renamed")
                .barcode("OLD-IMG-BARCODE")
                .barcodeType(BarcodeType.EXTERNAL)
                .sku("TAKEN-SKU")
                .isKit(false)
                .hasExpiration(false)
                .active(true)
                .build();

        MockMultipartFile image = new MockMultipartFile(
                "image", "new.png", MediaType.IMAGE_PNG_VALUE, new byte[100]);
        MockMultipartFile productPart = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(updateRequest));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/products/" + target.getId())
                .file(image)
                .file(productPart))
                .andExpect(status().isBadRequest());

        // The transaction never commits, so the deferred storage deletion must not run.
        verify(storageService, never()).deleteProductImages(any(), any());
    }

    @Test
    void processImagesShouldRequireAuth() throws Exception {
        mockMvc.perform(post("/api/admin/products/process-images"))
                .andExpect(status().isForbidden());
    }

    @Test
    void processImagesShouldReturnResultWithValidAuth() throws Exception {
        mockMvc.perform(post("/api/admin/products/process-images")
                .header("Authorization", "Bearer " + getValidToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.processed").isNumber())
                .andExpect(jsonPath("$.data.skipped").isNumber())
                .andExpect(jsonPath("$.data.compressed").isNumber())
                .andExpect(jsonPath("$.data.failed").isNumber());
    }

    @Test
    void processSingleImageWithProductId() throws Exception {
        var product = createTestProduct("Target", "TGT-BARCODE", "TGT-SKU");

        mockMvc.perform(post("/api/admin/products/process-images")
                .param("productId", product.getId().toString())
                .header("Authorization", "Bearer " + getValidToken()))
                .andExpect(status().isOk());
    }

    private String getValidToken() {
        return jwtTokenProvider.generateAccessToken(
                testUser.getId(),
                testTenant.getId(),
                testUser.getEmail(),
                List.of("ADMIN"),
                List.of("products:update"));
    }

    private Product createTestProduct(String name, String barcode, String sku) {
        Product product = new Product();
        product.setTenantId(testTenant.getId());
        product.setName(name);
        product.setCategory(testCategory);
        product.setBarcode(barcode);
        product.setBarcodeType(BarcodeType.EXTERNAL);
        product.setSku(sku);
        product.setIsKit(false);
        product.setHasExpiration(false);
        product.setActive(true);
        return productRepository.save(product);
    }
}
