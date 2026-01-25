package br.com.stockshift.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

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

@Transactional
class BatchDeletionIntegrationTest extends BaseIntegrationTest {

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

    private UUID tenantId;
    private UUID warehouseId;
    private UUID productId;
    private Warehouse testWarehouse;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        // Clean up all data
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        // Create test tenant and user
        Tenant testTenant = TestDataFactory.createTenant(
            tenantRepository,
            "Batch Deletion Test Tenant",
            "55555555000105"
        );
        tenantId = testTenant.getId();

        User testUser = TestDataFactory.createUser(
            userRepository,
            passwordEncoder,
            tenantId,
            "batchdeletion@test.com"
        );

        TenantContext.setTenantId(tenantId);

        // Create test category
        Category testCategory = TestDataFactory.createCategory(
            categoryRepository,
            tenantId,
            "Deletion Test Category"
        );

        // Create test warehouse
        testWarehouse = TestDataFactory.createWarehouse(
            warehouseRepository,
            tenantId,
            "Test Warehouse"
        );
        warehouseId = testWarehouse.getId();

        // Create test product
        testProduct = TestDataFactory.createProduct(
            productRepository,
            tenantId,
            testCategory,
            "Test Product",
            "SKU-DELETE-001"
        );
        productId = testProduct.getId();
    }

    @Test
    @WithMockUser(username = "batchdeletion@test.com", authorities = { "ROLE_ADMIN" })
    void shouldDeleteAllBatchesSuccessfully() throws Exception {
        // Create 3 batches for the same product and warehouse
        Batch batch1 = TestDataFactory.createBatch(
            batchRepository,
            tenantId,
            testProduct,
            testWarehouse,
            "BATCH-001",
            100
        );
        Batch batch2 = TestDataFactory.createBatch(
            batchRepository,
            tenantId,
            testProduct,
            testWarehouse,
            "BATCH-002",
            200
        );
        Batch batch3 = TestDataFactory.createBatch(
            batchRepository,
            tenantId,
            testProduct,
            testWarehouse,
            "BATCH-003",
            300
        );

        // Call DELETE endpoint
        mockMvc.perform(delete(
            "/api/batches/warehouses/{warehouseId}/products/{productId}/batches",
            warehouseId,
            productId
        ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount").value(3))
            .andExpect(jsonPath("$.productId").value(productId.toString()))
            .andExpect(jsonPath("$.warehouseId").value(warehouseId.toString()))
            .andExpect(jsonPath("$.message").value("Successfully deleted 3 batches"));

        // Verify batches are filtered by @SQLRestriction annotation
        List<Batch> remainingBatches = batchRepository.findByProductIdAndWarehouseIdAndTenantId(
            productId,
            warehouseId,
            tenantId
        );
        assertThat(remainingBatches).isEmpty();

        // Verify GET endpoint returns empty list
        mockMvc.perform(get(
            "/api/batches/warehouse/{warehouseId}",
            warehouseId
        ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data").isEmpty());
    }
}
