package br.com.stockshift.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.warehouse.BatchSellingPriceUpdateRequest;
import br.com.stockshift.dto.warehouse.BatchSellingPriceUpdateResponse;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;

@Transactional
class BatchSellingPriceUpdateIntegrationTest extends BaseIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

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

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;
    private UUID warehouseId;
    private UUID productId;
    private Warehouse testWarehouse;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant testTenant = TestDataFactory.createTenant(
            tenantRepository,
            "Selling Price Update Test Tenant",
            "66666666000106");
        tenantId = testTenant.getId();

        TestDataFactory.createUser(
            userRepository,
            passwordEncoder,
            tenantId,
            "sellingpriceupdate@test.com");

        TenantContext.setTenantId(tenantId);

        Category testCategory = TestDataFactory.createCategory(
            categoryRepository,
            tenantId,
            "Selling Price Update Category");

        testWarehouse = TestDataFactory.createWarehouse(
            warehouseRepository,
            tenantId,
            "Test Warehouse");
        warehouseId = testWarehouse.getId();

        testProduct = TestDataFactory.createProduct(
            productRepository,
            tenantId,
            testCategory,
            "Test Product",
            "SKU-SPU-001");
        productId = testProduct.getId();
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN" })
    void shouldUpdateSellingPriceForAllBatchesOfProductInWarehouse() throws Exception {
        // Create 3 batches with selling price 1500 (default)
        TestDataFactory.createBatch(batchRepository, tenantId, testProduct, testWarehouse, "BATCH-A", 10);
        TestDataFactory.createBatch(batchRepository, tenantId, testProduct, testWarehouse, "BATCH-B", 20);
        TestDataFactory.createBatch(batchRepository, tenantId, testProduct, testWarehouse, "BATCH-C", 30);

        Long newSellingPrice = 2000L;
        BatchSellingPriceUpdateRequest request = new BatchSellingPriceUpdateRequest(newSellingPrice);

        String responseContent = mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.affectedCount").value(3))
            .andExpect(jsonPath("$.data.productId").value(productId.toString()))
            .andExpect(jsonPath("$.data.warehouseId").value(warehouseId.toString()))
            .andReturn().getResponse().getContentAsString();

        ApiResponse<BatchSellingPriceUpdateResponse> response = objectMapper.readValue(
            responseContent,
            objectMapper.getTypeFactory().constructParametricType(
                ApiResponse.class,
                BatchSellingPriceUpdateResponse.class));

        assertThat(response.getData().affectedCount()).isEqualTo(3);
        assertThat(response.getData().message()).contains("Selling price updated for 3 batches");

        // Clear persistence context to force reload from database
        entityManager.clear();

        // Verify each batch was updated
        List<Batch> batches = batchRepository.findByProductIdAndWarehouseIdAndTenantId(
            productId, warehouseId, tenantId);
        assertThat(batches).hasSize(3);
        assertThat(batches).allMatch(b -> b.getSellingPrice().equals(newSellingPrice));
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN" })
    void shouldReturn200WithZeroCountWhenNoBatchesExist() throws Exception {
        // Don't create any batches
        Long newSellingPrice = 2000L;
        BatchSellingPriceUpdateRequest request = new BatchSellingPriceUpdateRequest(newSellingPrice);

        String responseContent = mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        ApiResponse<BatchSellingPriceUpdateResponse> response = objectMapper.readValue(
            responseContent,
            objectMapper.getTypeFactory().constructParametricType(
                ApiResponse.class,
                BatchSellingPriceUpdateResponse.class));

        assertThat(response.getData().affectedCount()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN" })
    void shouldReturn404WhenWarehouseNotFound() throws Exception {
        UUID nonExistentWarehouseId = UUID.randomUUID();
        BatchSellingPriceUpdateRequest request = new BatchSellingPriceUpdateRequest(2000L);

        mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                nonExistentWarehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN" })
    void shouldReturn404WhenProductNotFound() throws Exception {
        UUID nonExistentProductId = UUID.randomUUID();
        BatchSellingPriceUpdateRequest request = new BatchSellingPriceUpdateRequest(2000L);

        mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, nonExistentProductId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = { "BATCH_READ" })
    void shouldReturn403WhenInsufficientPermission() throws Exception {
        BatchSellingPriceUpdateRequest request = new BatchSellingPriceUpdateRequest(2000L);

        mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN" })
    void shouldReturn400WhenSellingPriceIsNull() throws Exception {
        String body = "{}";

        mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN" })
    void shouldReturn400WhenSellingPriceIsNegative() throws Exception {
        String body = "{\"sellingPrice\": -100}";

        mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "sellingpriceupdate@test.com", authorities = { "ROLE_ADMIN", "BATCH_CREATE", "BATCH_UPDATE", "BATCH_READ" })
    void shouldRespectTenantIsolation() throws Exception {
        // Create a batch for the current tenant
        TestDataFactory.createBatch(batchRepository, tenantId, testProduct, testWarehouse, "BATCH-ISO", 10);

        // Switch to a different tenant
        UUID differentTenantId = UUID.randomUUID();
        TenantContext.setTenantId(differentTenantId);

        BatchSellingPriceUpdateRequest request = new BatchSellingPriceUpdateRequest(9999L);
        // The warehouse does not exist for the different tenant → 404
        mockMvc.perform(patch(
                "/api/batches/warehouses/{warehouseId}/products/{productId}/batches/selling-price",
                warehouseId, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());

        // Switch back to original tenant
        TenantContext.setTenantId(tenantId);

        // Verify the batch still has the original selling price (not updated)
        List<Batch> batches = batchRepository.findByProductIdAndWarehouseIdAndTenantId(
            productId, warehouseId, tenantId);
        assertThat(batches).hasSize(1);
        assertThat(batches.get(0).getSellingPrice()).isEqualTo(1500L);
    }
}
