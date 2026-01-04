package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.warehouse.BatchRequest;
import br.com.stockshift.dto.warehouse.ProductBatchRequest;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class BatchControllerIntegrationTest extends BaseIntegrationTest {

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

        testTenant = TestDataFactory.createTenant(tenantRepository, "Batch Test Tenant", "44444444000104");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "batch@test.com");

        TenantContext.setTenantId(testTenant.getId());

        testCategory = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Test Category");
        testProduct = TestDataFactory.createProduct(productRepository, testTenant.getId(),
                testCategory, "Test Product", "SKU-001");
        testWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Main Storage");
    }

    @Test
    @WithMockUser(username = "batch@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCreateBatch() throws Exception {
        BatchRequest request = BatchRequest.builder()
                .productId(testProduct.getId())
                .warehouseId(testWarehouse.getId())
                .batchCode("BATCH-20251228-001")
                .quantity(100)
                .costPrice(BigDecimal.valueOf(15.50))
                .sellingPrice(BigDecimal.valueOf(25.00))
                .expirationDate(LocalDate.now().plusMonths(12))
                .build();

        mockMvc.perform(post("/api/batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.costPrice").value(15.50));
    }

    @Test
    @WithMockUser(username = "batch@test.com", authorities = {"ROLE_ADMIN"})
    void shouldGetBatchById() throws Exception {
        Batch batch = TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                testProduct, testWarehouse, 50);

        mockMvc.perform(get("/api/batches/{id}", batch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(batch.getId().toString()))
                .andExpect(jsonPath("$.data.quantity").value(50));
    }

    @Test
    @WithMockUser(username = "batch@test.com", authorities = {"ROLE_ADMIN"})
    void shouldFindBatchesByWarehouse() throws Exception {
        TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                testProduct, testWarehouse, 30);
        TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                testProduct, testWarehouse, 40);

        mockMvc.perform(get("/api/batches/warehouse/{warehouseId}", testWarehouse.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @WithMockUser(username = "batch@test.com", authorities = {"ROLE_ADMIN"})
    void shouldFindExpiringBatches() throws Exception {
        // Create batch expiring in 15 days
        Batch expiringBatch = new Batch();
        expiringBatch.setTenantId(testTenant.getId());
        expiringBatch.setProduct(testProduct);
        expiringBatch.setWarehouse(testWarehouse);
        expiringBatch.setBatchCode("BATCH-EXP-001");
        expiringBatch.setQuantity(25);
        expiringBatch.setCostPrice(BigDecimal.valueOf(10.00));
        expiringBatch.setSellingPrice(BigDecimal.valueOf(15.00));
        expiringBatch.setExpirationDate(LocalDate.now().plusDays(15));
        batchRepository.save(expiringBatch);

        mockMvc.perform(get("/api/batches/expiring/{daysAhead}", 30))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @WithMockUser(username = "batch@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCreateProductWithBatchSuccessfully() throws Exception {
        ProductBatchRequest request = ProductBatchRequest.builder()
                .name("New Product")
                .sku("SKU-NEW-001")
                .barcode("9876543210")
                .warehouseId(testWarehouse.getId())
                .batchCode("BATCH-NEW-001")
                .quantity(50)
                .costPrice(BigDecimal.valueOf(12.00))
                .sellingPrice(BigDecimal.valueOf(22.00))
                .build();

        mockMvc.perform(post("/api/batches/with-product")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.product").isNotEmpty())
                .andExpect(jsonPath("$.data.batch").isNotEmpty())
                .andExpect(jsonPath("$.data.product.name").value("New Product"))
                .andExpect(jsonPath("$.data.batch.quantity").value(50));
    }

    @Test
    @WithMockUser(username = "batch@test.com", authorities = {"ROLE_ADMIN"})
    void shouldReturn409WhenSkuAlreadyExists() throws Exception {
        ProductBatchRequest request = ProductBatchRequest.builder()
                .name("Duplicate Product")
                .sku(testProduct.getSku()) // Use existing SKU
                .warehouseId(testWarehouse.getId())
                .batchCode("BATCH-DUP-001")
                .quantity(50)
                .build();

        mockMvc.perform(post("/api/batches/with-product")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already exists")));
    }
}
