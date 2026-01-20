package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import br.com.stockshift.dto.validation.ScanRequest;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.MovementType;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class TransferValidationControllerIntegrationTest extends BaseIntegrationTest {

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

    @Autowired
    private TransferValidationRepository transferValidationRepository;

    private Tenant testTenant;
    private User testUser;
    private Category testCategory;
    private Product testProduct;
    private Warehouse sourceWarehouse;
    private Warehouse destWarehouse;
    private Batch testBatch;

    @BeforeEach
    void setUpTestData() {
        transferValidationRepository.deleteAll();
        stockMovementRepository.deleteAll();
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Validation Test Tenant", "66666666000106");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "validation@test.com");

        TenantContext.setTenantId(testTenant.getId());

        testCategory = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Validation Category");
        testProduct = TestDataFactory.createProduct(productRepository, testTenant.getId(),
                testCategory, "Validation Product", "SKU-VAL-001");
        testProduct.setBarcode("7891234567890");
        productRepository.save(testProduct);

        sourceWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Source Warehouse");
        destWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Destination Warehouse");
        testBatch = TestDataFactory.createBatch(batchRepository, testTenant.getId(),
                testProduct, sourceWarehouse, 100);
    }

    @Test
    @WithMockUser(username = "validation@test.com", authorities = {"ROLE_ADMIN"})
    void shouldStartValidationForInTransitTransfer() throws Exception {
        String movementId = createAndExecuteTransfer();

        mockMvc.perform(post("/api/stock-movements/{id}/validations", movementId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.validationId").exists())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].expectedQuantity").value(10));
    }

    @Test
    @WithMockUser(username = "validation@test.com", authorities = {"ROLE_ADMIN"})
    void shouldScanBarcodeSuccessfully() throws Exception {
        String movementId = createAndExecuteTransfer();

        String validationResponse = mockMvc.perform(post("/api/stock-movements/{id}/validations", movementId))
                .andReturn().getResponse().getContentAsString();
        String validationId = objectMapper.readTree(validationResponse).get("data").get("validationId").asText();

        ScanRequest scanRequest = ScanRequest.builder().barcode("7891234567890").build();

        mockMvc.perform(post("/api/stock-movements/{movementId}/validations/{validationId}/scan",
                        movementId, validationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scanRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.item.scannedQuantity").value(1));
    }

    @Test
    @WithMockUser(username = "validation@test.com", authorities = {"ROLE_ADMIN"})
    void shouldRejectUnknownBarcode() throws Exception {
        String movementId = createAndExecuteTransfer();

        String validationResponse = mockMvc.perform(post("/api/stock-movements/{id}/validations", movementId))
                .andReturn().getResponse().getContentAsString();
        String validationId = objectMapper.readTree(validationResponse).get("data").get("validationId").asText();

        ScanRequest scanRequest = ScanRequest.builder().barcode("UNKNOWN123").build();

        mockMvc.perform(post("/api/stock-movements/{movementId}/validations/{validationId}/scan",
                        movementId, validationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scanRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.message").value("Produto não faz parte desta transferência"));
    }

    @Test
    @WithMockUser(username = "validation@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCompleteValidationWithDiscrepancy() throws Exception {
        String movementId = createAndExecuteTransfer();

        String validationResponse = mockMvc.perform(post("/api/stock-movements/{id}/validations", movementId))
                .andReturn().getResponse().getContentAsString();
        String validationId = objectMapper.readTree(validationResponse).get("data").get("validationId").asText();

        ScanRequest scanRequest = ScanRequest.builder().barcode("7891234567890").build();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/stock-movements/{movementId}/validations/{validationId}/scan",
                            movementId, validationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(scanRequest)));
        }

        mockMvc.perform(post("/api/stock-movements/{movementId}/validations/{validationId}/complete",
                        movementId, validationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED_WITH_DISCREPANCY"))
                .andExpect(jsonPath("$.data.summary.totalExpected").value(10))
                .andExpect(jsonPath("$.data.summary.totalReceived").value(5))
                .andExpect(jsonPath("$.data.summary.totalMissing").value(5))
                .andExpect(jsonPath("$.data.discrepancies[0].missing").value(5));
    }

    @Test
    @WithMockUser(username = "validation@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCompleteValidationWithoutDiscrepancy() throws Exception {
        String movementId = createAndExecuteTransfer();

        String validationResponse = mockMvc.perform(post("/api/stock-movements/{id}/validations", movementId))
                .andReturn().getResponse().getContentAsString();
        String validationId = objectMapper.readTree(validationResponse).get("data").get("validationId").asText();

        ScanRequest scanRequest = ScanRequest.builder().barcode("7891234567890").build();
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/stock-movements/{movementId}/validations/{validationId}/scan",
                            movementId, validationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(scanRequest)));
        }

        mockMvc.perform(post("/api/stock-movements/{movementId}/validations/{validationId}/complete",
                        movementId, validationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.discrepancies").isEmpty());
    }

    private String createAndExecuteTransfer() throws Exception {
        StockMovementItemRequest itemRequest = StockMovementItemRequest.builder()
                .productId(testProduct.getId())
                .batchId(testBatch.getId())
                .quantity(10)
                .build();

        StockMovementRequest request = StockMovementRequest.builder()
                .movementType(MovementType.TRANSFER)
                .sourceWarehouseId(sourceWarehouse.getId())
                .destinationWarehouseId(destWarehouse.getId())
                .items(Collections.singletonList(itemRequest))
                .notes("Test transfer")
                .build();

        String createResponse = mockMvc.perform(post("/api/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String movementId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        mockMvc.perform(post("/api/stock-movements/{id}/execute", movementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));

        return movementId;
    }
}
