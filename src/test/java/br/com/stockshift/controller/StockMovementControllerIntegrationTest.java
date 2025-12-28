package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.movement.StockMovementItemRequest;
import br.com.stockshift.dto.movement.StockMovementRequest;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.MovementType;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class StockMovementControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private StockMovementRepository stockMovementRepository;

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
    private Batch testBatch;

    @BeforeEach
    void setUpTestData() {
        stockMovementRepository.deleteAll();
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Movement Test Tenant", "55555555000105");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "movement@test.com");

        TenantContext.setTenantId(testTenant.getId());

        testCategory = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Movement Category");
        testProduct = TestDataFactory.createProduct(productRepository, testTenant.getId(),
                testCategory, "Movement Product", "SKU-MOV-001");
        testWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Movement Warehouse");
        testBatch = TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                testProduct, testWarehouse, 100);
    }

    @Test
    @WithMockUser(username = "movement@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCreateStockMovement() throws Exception {
        StockMovementItemRequest itemRequest = StockMovementItemRequest.builder()
                .productId(testProduct.getId())
                .batchId(testBatch.getId())
                .quantity(20)
                .build();

        StockMovementRequest request = StockMovementRequest.builder()
                .movementType(MovementType.PURCHASE)
                .destinationWarehouseId(testWarehouse.getId())
                .items(Collections.singletonList(itemRequest))
                .notes("Test purchase")
                .build();

        mockMvc.perform(post("/api/stock-movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.movementType").value("PURCHASE"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "movement@test.com", authorities = {"ROLE_ADMIN"})
    void shouldExecuteStockMovement() throws Exception {
        // Create movement
        StockMovementItemRequest itemRequest = StockMovementItemRequest.builder()
                .productId(testProduct.getId())
                .batchId(testBatch.getId())
                .quantity(15)
                .build();

        StockMovementRequest request = StockMovementRequest.builder()
                .movementType(MovementType.PURCHASE)
                .destinationWarehouseId(testWarehouse.getId())
                .items(Collections.singletonList(itemRequest))
                .build();

        String createResponse = mockMvc.perform(post("/api/stock-movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        String movementId = objectMapper.readTree(createResponse)
                .get("data").get("id").asText();

        // Execute movement
        mockMvc.perform(post("/api/stock-movements/{id}/execute", movementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // Verify stock was updated
        Batch updatedBatch = batchRepository.findById(testBatch.getId()).orElseThrow();
        assert updatedBatch.getQuantity() == 115; // 100 + 15
    }

    @Test
    @WithMockUser(username = "movement@test.com", authorities = {"ROLE_ADMIN"})
    void shouldGetStockMovementById() throws Exception {
        StockMovementItemRequest itemRequest = StockMovementItemRequest.builder()
                .productId(testProduct.getId())
                .batchId(testBatch.getId())
                .quantity(10)
                .build();

        StockMovementRequest request = StockMovementRequest.builder()
                .movementType(MovementType.ADJUSTMENT)
                .destinationWarehouseId(testWarehouse.getId())
                .items(Collections.singletonList(itemRequest))
                .build();

        String createResponse = mockMvc.perform(post("/api/stock-movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        String movementId = objectMapper.readTree(createResponse)
                .get("data").get("id").asText();

        mockMvc.perform(get("/api/stock-movements/{id}", movementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(movementId))
                .andExpect(jsonPath("$.data.movementType").value("ADJUSTMENT"));
    }
}
