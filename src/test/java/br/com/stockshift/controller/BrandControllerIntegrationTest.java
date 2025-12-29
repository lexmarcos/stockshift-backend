package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.brand.BrandRequest;
import br.com.stockshift.model.entity.Brand;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class BrandControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private BrandRepository brandRepository;

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

    @BeforeEach
    void setUpTestData() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Brand Test Tenant", "33333333000103");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "brand@test.com");

        TenantContext.setTenantId(testTenant.getId());
    }

    @Test
    @WithMockUser(username = "brand@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCreateBrand() throws Exception {
        BrandRequest request = new BrandRequest();
        request.setName("Nike");
        request.setLogoUrl("https://example.com/nike-logo.png");

        mockMvc.perform(post("/api/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Nike"))
                .andExpect(jsonPath("$.data.logoUrl").value("https://example.com/nike-logo.png"));
    }

    @Test
    @WithMockUser(username = "brand@test.com", authorities = {"ROLE_ADMIN"})
    void shouldNotCreateBrandWithDuplicateName() throws Exception {
        TestDataFactory.createBrand(brandRepository, testTenant.getId(), "Adidas");

        BrandRequest request = new BrandRequest();
        request.setName("Adidas");

        mockMvc.perform(post("/api/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(username = "brand@test.com", authorities = {"ROLE_ADMIN"})
    void shouldListAllBrands() throws Exception {
        TestDataFactory.createBrand(brandRepository, testTenant.getId(), "Brand 1");
        TestDataFactory.createBrand(brandRepository, testTenant.getId(), "Brand 2");

        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @WithMockUser(username = "brand@test.com", authorities = {"ROLE_ADMIN"})
    void shouldGetBrandById() throws Exception {
        Brand brand = TestDataFactory.createBrand(brandRepository, testTenant.getId(), "Puma");

        mockMvc.perform(get("/api/brands/{id}", brand.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(brand.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Puma"));
    }

    @Test
    @WithMockUser(username = "brand@test.com", authorities = {"ROLE_ADMIN"})
    void shouldReturnNotFoundForNonExistentBrand() throws Exception {
        mockMvc.perform(get("/api/brands/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockUser(username = "brand@test.com", authorities = {"ROLE_ADMIN"})
    void shouldUpdateBrand() throws Exception {
        Brand brand = TestDataFactory.createBrand(brandRepository, testTenant.getId(), "Old Brand");

        BrandRequest request = new BrandRequest();
        request.setName("Updated Brand");
        request.setLogoUrl("https://example.com/updated-logo.png");

        mockMvc.perform(put("/api/brands/{id}", brand.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Brand"))
                .andExpect(jsonPath("$.data.logoUrl").value("https://example.com/updated-logo.png"));
    }

    @Test
    @WithMockUser(username = "brand@test.com", authorities = {"ROLE_ADMIN"})
    void shouldNotUpdateBrandWithDuplicateName() throws Exception {
        Brand brand1 = TestDataFactory.createBrand(brandRepository, testTenant.getId(), "Brand One");
        Brand brand2 = TestDataFactory.createBrand(brandRepository, testTenant.getId(), "Brand Two");

        BrandRequest request = new BrandRequest();
        request.setName("Brand One");

        mockMvc.perform(put("/api/brands/{id}", brand2.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(username = "brand@test.com", authorities = {"ROLE_ADMIN"})
    void shouldDeleteBrandWithoutProducts() throws Exception {
        Brand brand = TestDataFactory.createBrand(brandRepository, testTenant.getId(), "Brand to Delete");

        mockMvc.perform(delete("/api/brands/{id}", brand.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Brand deleted successfully"));
    }

    @Test
    @WithMockUser(username = "brand@test.com", authorities = {"ROLE_ADMIN"})
    void shouldNotDeleteBrandWithProducts() throws Exception {
        Brand brand = TestDataFactory.createBrand(brandRepository, testTenant.getId(), "Brand with Products");
        Category category = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Test Category");

        Product product = TestDataFactory.createProduct(productRepository, testTenant.getId(), category, "Test Product", "SKU-001");
        product.setBrand(brand);
        productRepository.save(product);

        mockMvc.perform(delete("/api/brands/{id}", brand.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.status").value(400));
    }
}
