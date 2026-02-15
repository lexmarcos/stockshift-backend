package br.com.stockshift.controller;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.security.WarehouseContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransferControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private UserRepository userRepository;

    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private Product testProduct;
    private Batch testBatch;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        
        // Setup Warehouses
        sourceWarehouse = new Warehouse();
        sourceWarehouse.setName("Source Warehouse");
        sourceWarehouse.setCode("SRC-WH-01");
        sourceWarehouse.setCity("Source City");
        sourceWarehouse.setState("SP");
        sourceWarehouse.setAddress("Source Address 123");
        sourceWarehouse.setIsActive(true);
        sourceWarehouse.setTenantId(tenantId);
        sourceWarehouse = warehouseRepository.save(sourceWarehouse);

        destinationWarehouse = new Warehouse();
        destinationWarehouse.setName("Destination Warehouse");
        destinationWarehouse.setCode("DST-WH-01");
        destinationWarehouse.setCity("Destination City");
        destinationWarehouse.setState("RJ");
        destinationWarehouse.setAddress("Destination Address 456");
        destinationWarehouse.setIsActive(true);
        destinationWarehouse.setTenantId(tenantId);
        destinationWarehouse = warehouseRepository.save(destinationWarehouse);

        // Setup Product
        testProduct = new Product();
        testProduct.setName("Test Product");
        testProduct.setSku("SKU-123");
        testProduct.setBarcode("1234567890");
        testProduct.setTenantId(tenantId);
        testProduct = productRepository.save(testProduct);

        // Setup Batch
        testBatch = Batch.builder()
                .product(testProduct)
                .warehouse(sourceWarehouse)
                .batchCode("BATCH-001")
                .quantity(new BigDecimal("100"))
                .manufacturedDate(LocalDate.now())
                .expirationDate(LocalDate.now().plusYears(1))
                .costPrice(1000L)
                .sellingPrice(1500L)
                .build();
        testBatch.setTenantId(tenantId);
        testBatch = batchRepository.save(testBatch);

        // Setup User for @WithMockUser username resolution in SecurityUtils
        User testUser = new User();
        testUser.setTenantId(tenantId);
        testUser.setEmail("admin@test.com");
        testUser.setPassword("encoded-password");
        testUser.setFullName("Admin User");
        testUser.setIsActive(true);
        testUser.setMustChangePassword(false);
        userRepository.save(testUser);
        
        // Set context for source warehouse by default
        WarehouseContext.setWarehouseId(sourceWarehouse.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        WarehouseContext.clear();
        transferRepository.deleteAll();
        batchRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        warehouseRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = {"ROLE_ADMIN", "TRANSFER_CREATE"})
    void shouldCreateTransfer() throws Exception {
        CreateTransferRequest request = CreateTransferRequest.builder()
                .destinationWarehouseId(destinationWarehouse.getId())
                .notes("Test transfer")
                .items(List.of(CreateTransferItemRequest.builder()
                        .sourceBatchId(testBatch.getId())
                        .quantity(new BigDecimal("10"))
                        .build()))
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = {"ROLE_ADMIN"})
    void shouldListTransfers() throws Exception {
        mockMvc.perform(get("/api/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = {"ROLE_ADMIN", "TRANSFER_EXECUTE"})
    void shouldExecuteTransfer() throws Exception {
        // Create transfer in DRAFT status first
        Transfer transfer = Transfer.builder()
                .code("TRF-TEST-1")
                .sourceWarehouseId(sourceWarehouse.getId())
                .destinationWarehouseId(destinationWarehouse.getId())
                .status(TransferStatus.DRAFT)
                .createdByUserId(UUID.randomUUID())
                .build();
        transfer.setTenantId(tenantId);
        
        TransferItem item = TransferItem.builder()
                .sourceBatchId(testBatch.getId())
                .productId(testProduct.getId())
                .productBarcode(testProduct.getBarcode())
                .productName(testProduct.getName())
                .productSku(testProduct.getSku())
                .quantitySent(new BigDecimal("10"))
                .quantityReceived(BigDecimal.ZERO)
                .build();
        transfer.addItem(item);
        
        transfer = transferRepository.save(transfer);

        mockMvc.perform(post("/api/transfers/{id}/execute", transfer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = {"ROLE_ADMIN", "TRANSFER_VALIDATE"})
    void shouldScanBarcode() throws Exception {
        // Create and execute transfer first
        Transfer transfer = Transfer.builder()
                .code("TRF-TEST-2")
                .sourceWarehouseId(sourceWarehouse.getId())
                .destinationWarehouseId(destinationWarehouse.getId())
                .status(TransferStatus.IN_TRANSIT)
                .createdByUserId(UUID.randomUUID())
                .build();
        transfer.setTenantId(tenantId);

        TransferItem item = TransferItem.builder()
                .sourceBatchId(testBatch.getId())
                .productId(testProduct.getId())
                .productBarcode(testProduct.getBarcode())
                .productName(testProduct.getName())
                .productSku(testProduct.getSku())
                .quantitySent(new BigDecimal("10"))
                .quantityReceived(BigDecimal.ZERO)
                .build();
        transfer.addItem(item);
        transferRepository.save(transfer);
        
        // Start validation
        // Switch context to destination warehouse for validation
        WarehouseContext.setWarehouseId(destinationWarehouse.getId());
        
        mockMvc.perform(post("/api/transfers/{id}/start-validation", transfer.getId()))
                .andExpect(status().isOk());

        ScanBarcodeRequest request = ScanBarcodeRequest.builder()
                .barcode(testProduct.getBarcode())
                .build();

        mockMvc.perform(post("/api/transfers/{id}/scan", transfer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }
}
