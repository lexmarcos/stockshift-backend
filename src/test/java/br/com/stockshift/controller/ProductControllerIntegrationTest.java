package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.enums.BarcodeType;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.repository.CategoryRepository;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import br.com.stockshift.dto.ai.ProductClassificationResponse;
import br.com.stockshift.service.OpenAiService;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class ProductControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private OpenAiService openAiService;

    private Tenant testTenant;
    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUpTestData() {
        // Clear any existing data
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();
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
