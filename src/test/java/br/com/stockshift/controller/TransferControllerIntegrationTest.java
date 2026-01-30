package br.com.stockshift.controller;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.*;
import br.com.stockshift.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransferControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private InventoryLedgerRepository ledgerRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant testTenant;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private Product product;
    private Batch batch;

    @BeforeEach
    void setUp() {
        transferRepository.deleteAll();
        ledgerRepository.deleteAll();
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Transfer Test Tenant", "22222222000102");
        TestDataFactory.createUser(userRepository, passwordEncoder, testTenant.getId(), "transfer@test.com");
        Category testCategory = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Test Category");

        sourceWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Source Warehouse");
        sourceWarehouse.setCode("WH-SRC-01");
        warehouseRepository.save(sourceWarehouse);

        destinationWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Destination Warehouse");
        destinationWarehouse.setCode("WH-DST-01");
        warehouseRepository.save(destinationWarehouse);

        product = TestDataFactory.createProduct(productRepository, testTenant.getId(), testCategory, "Test Product", "TEST-SKU");
        product.setBarcode("1234567890");
        productRepository.save(product);

        batch = TestDataFactory.createBatch(batchRepository, testTenant.getId(), product, sourceWarehouse, "BATCH-001", 100);
    }

    @Test
    @WithMockUser(username = "transfer@test.com", authorities = {"TRANSFER:CREATE", "TRANSFER:READ", "TRANSFER:EXECUTE", "TRANSFER:VALIDATE"})
    void shouldCompleteFullTransferFlow() throws Exception {
        // 1. Create Transfer
        CreateTransferRequest createRequest = new CreateTransferRequest();
        createRequest.setSourceWarehouseId(sourceWarehouse.getId());
        createRequest.setDestinationWarehouseId(destinationWarehouse.getId());

        TransferItemRequest itemRequest = new TransferItemRequest();
        itemRequest.setProductId(product.getId());
        itemRequest.setBatchId(batch.getId());
        itemRequest.setQuantity(new BigDecimal("50.000"));
        createRequest.setItems(List.of(itemRequest));

        String createResponse = mockMvc.perform(post("/stockshift/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.transferCode").exists())
            .andReturn().getResponse().getContentAsString();

        UUID transferId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        // 2. Dispatch Transfer
        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/dispatch"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        // Verify stock was reduced
        Batch updatedBatch = batchRepository.findById(batch.getId()).orElseThrow();
        assertThat(updatedBatch.getQuantity()).isEqualByComparingTo(new BigDecimal("50.000"));

        // 3. Start Validation
        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/validation/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VALIDATION_IN_PROGRESS"));

        // 4. Scan Item
        ScanItemRequest scanRequest = new ScanItemRequest();
        scanRequest.setBarcode("1234567890");
        scanRequest.setQuantity(new BigDecimal("50.000"));

        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/validation/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(scanRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itemsScanned").value(1));

        // 5. Complete Validation
        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/validation/complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Verify destination batch was created
        List<Batch> destinationBatches = batchRepository.findByWarehouseId(destinationWarehouse.getId());
        assertThat(destinationBatches).hasSize(1);
        assertThat(destinationBatches.get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("50.000"));
    }

    @Test
    @WithMockUser(username = "transfer@test.com", authorities = {"TRANSFER:CREATE", "TRANSFER:READ", "TRANSFER:EXECUTE", "TRANSFER:VALIDATE"})
    void shouldCompleteWithDiscrepancyOnShortage() throws Exception {
        // Create and dispatch
        CreateTransferRequest createRequest = new CreateTransferRequest();
        createRequest.setSourceWarehouseId(sourceWarehouse.getId());
        createRequest.setDestinationWarehouseId(destinationWarehouse.getId());
        createRequest.setItems(List.of(new TransferItemRequest(product.getId(), batch.getId(), new BigDecimal("50"))));

        String createResponse = mockMvc.perform(post("/stockshift/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andReturn().getResponse().getContentAsString();
        UUID transferId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/dispatch")).andExpect(status().isOk());
        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/validation/start")).andExpect(status().isOk());

        // Scan only 40 (expected 50)
        ScanItemRequest scanRequest = new ScanItemRequest("1234567890", new BigDecimal("40"), null);
        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/validation/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(scanRequest))).andExpect(status().isOk());

        // Complete
        mockMvc.perform(post("/stockshift/transfers/" + transferId + "/validation/complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED_WITH_DISCREPANCY"))
            .andExpect(jsonPath("$.hasDiscrepancy").value(true));
    }
}
