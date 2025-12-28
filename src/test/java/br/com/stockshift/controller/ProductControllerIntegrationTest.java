package br.com.stockshift.controller;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.enums.BarcodeType;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant testTenant;
    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUpTestData() {
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

        // Set security context
        TenantContext.setTenantId(testTenant.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(testUser.getEmail(), null)
        );

        // Create test category
        testCategory = new Category();
        testCategory.setTenantId(testTenant.getId());
        testCategory.setName("Test Category");
        testCategory = categoryRepository.save(testCategory);
    }

    @Test
    @WithMockUser(username = "test@example.com", authorities = {"PRODUCT_CREATE", "ADMIN"})
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

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Test Product"))
                .andExpect(jsonPath("$.data.barcode").value("1234567890"))
                .andExpect(jsonPath("$.data.sku").value("TEST-SKU-001"));
    }

    @Test
    @WithMockUser(username = "test@example.com", authorities = {"PRODUCT_READ", "ADMIN"})
    void shouldGetProductById() throws Exception {
        Product product = createTestProduct("Get Test Product", "BARCODE-001", "SKU-001");

        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Get Test Product"));
    }

    @Test
    @WithMockUser(username = "test@example.com", authorities = {"PRODUCT_READ", "ADMIN"})
    void shouldFindProductByBarcode() throws Exception {
        Product product = createTestProduct("Barcode Test", "BARCODE-FIND", "SKU-002");

        mockMvc.perform(get("/api/products/barcode/{barcode}", "BARCODE-FIND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.barcode").value("BARCODE-FIND"));
    }

    @Test
    @WithMockUser(username = "test@example.com", authorities = {"PRODUCT_UPDATE", "ADMIN"})
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

        mockMvc.perform(put("/api/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Product Name"))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @WithMockUser(username = "test@example.com", authorities = {"PRODUCT_DELETE", "ADMIN"})
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
    @WithMockUser(username = "test@example.com", authorities = {"PRODUCT_READ", "ADMIN"})
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
