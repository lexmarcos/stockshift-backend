package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class BatchSoftDeleteTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant testTenant;
    private User testUser;
    private Category testCategory;
    private Product testProduct;
    private Warehouse testWarehouse;

    @BeforeEach
    void setUpTestData() {
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Soft Delete Test Tenant", "44444444000104");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "softdelete@test.com");

        TenantContext.setTenantId(testTenant.getId());

        testCategory = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Test Category");
        testProduct = TestDataFactory.createProduct(productRepository, testTenant.getId(),
                testCategory, "Test Product", "SKU-SD-001");
        testWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(),
                "Soft Delete Storage");
    }

    @Test
    @WithMockUser(username = "softdelete@test.com", authorities = { "ROLE_ADMIN" })
    void shouldNotReturnDeletedBatchesInFindAll() throws Exception {
        // 1. Create two batches
        Batch batch1 = TestDataFactory.createBatch(batchRepository, testTenant.getId(), testProduct, testWarehouse, 10);
        Batch batch2 = TestDataFactory.createBatch(batchRepository, testTenant.getId(), testProduct, testWarehouse, 20);

        // Verify both exist
        mockMvc.perform(get("/api/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 2. Delete batch1 via API
        mockMvc.perform(delete("/api/batches/{id}", batch1.getId()))
                .andExpect(status().isOk());

        // 3. Verify findAll only returns batch2
        mockMvc.perform(get("/api/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(batch2.getId().toString()));
    }
}
