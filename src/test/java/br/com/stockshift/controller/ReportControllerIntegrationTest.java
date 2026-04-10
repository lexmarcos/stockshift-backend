package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.security.WarehouseContext;
import br.com.stockshift.util.TestDataFactory;

class ReportControllerIntegrationTest extends BaseIntegrationTest {

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
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockMovementItemRepository stockMovementItemRepository;

    @Autowired
    private TransferRepository transferRepository;

    private Tenant testTenant;

    @BeforeEach
    void setUpTestData() {
        batchRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        stockMovementItemRepository.deleteAll();
        stockMovementRepository.deleteAll();
        transferRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Report Test Tenant", "66666666000106");
        TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "report@test.com");

        TenantContext.setTenantId(testTenant.getId());

        // Create test data for reports
        Category category = TestDataFactory.createCategory(categoryRepository, testTenant.getId(), "Report Category");
        Product product = TestDataFactory.createProduct(productRepository, testTenant.getId(),
                category, "Report Product", "SKU-RPT-001");
        Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(),
                "Report Warehouse");
        TestDataFactory.createBatch(batchRepository, testTenant.getId(), product, warehouse, 75);
    }

    @Test
    @WithMockUser(username = "report@test.com", authorities = { "ROLE_ADMIN" })
    void shouldGetDashboard() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.totalProducts").exists())
                .andExpect(jsonPath("$.data.totalWarehouses").exists());
    }

    @Test
    @WithMockUser(username = "report@test.com", authorities = { "ROLE_ADMIN" })
    void shouldGetDashboardAcrossAllWarehousesEvenWithWarehouseContext() throws Exception {
        Category secondCategory = TestDataFactory.createCategory(
                categoryRepository,
                testTenant.getId(),
                "Secondary Report Category"
        );
        Product secondProduct = TestDataFactory.createProduct(
                productRepository,
                testTenant.getId(),
                secondCategory,
                "Second Report Product",
                "SKU-RPT-002"
        );
        Warehouse secondWarehouse = TestDataFactory.createWarehouse(
                warehouseRepository,
                testTenant.getId(),
                "Overflow Warehouse"
        );
        TestDataFactory.createBatch(batchRepository, testTenant.getId(), secondProduct, secondWarehouse, 20);

        Warehouse primaryWarehouse = warehouseRepository.findAllByTenantId(testTenant.getId()).stream()
                .filter(warehouse -> "Report Warehouse".equals(warehouse.getName()))
                .findFirst()
                .orElseThrow();

        WarehouseContext.setWarehouseId(primaryWarehouse.getId());

        try {
            mockMvc.perform(get("/api/reports/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalWarehouses").value(2))
                    .andExpect(jsonPath("$.data.stockByWarehouse.length()").value(2));
        } finally {
            WarehouseContext.clear();
        }
    }

    @Test
    @WithMockUser(username = "report@test.com", authorities = { "ROLE_ADMIN" })
    void shouldDeduplicateTransferLegsInDashboardRecentMovements() throws Exception {
        Warehouse sourceWarehouse = warehouseRepository.findAllByTenantId(testTenant.getId()).stream()
                .filter(warehouse -> "Report Warehouse".equals(warehouse.getName()))
                .findFirst()
                .orElseThrow();
        User reportUser = userRepository.findByTenantIdAndEmail(testTenant.getId(), "report@test.com")
                .orElseThrow();

        Category transferCategory = TestDataFactory.createCategory(
                categoryRepository,
                testTenant.getId(),
                "Transfer Category"
        );
        Product transferProduct = TestDataFactory.createProduct(
                productRepository,
                testTenant.getId(),
                transferCategory,
                "Transfer Product",
                "SKU-RPT-TRF"
        );
        Warehouse destinationWarehouse = TestDataFactory.createWarehouse(
                warehouseRepository,
                testTenant.getId(),
                "Filial Sul"
        );
        Batch destinationBatch = TestDataFactory.createBatch(
                batchRepository,
                testTenant.getId(),
                transferProduct,
                destinationWarehouse,
                10
        );

        UUID transferReferenceId = UUID.randomUUID();

        StockMovement transferOut = TestDataFactory.createStockMovement(
                stockMovementRepository,
                testTenant.getId(),
                sourceWarehouse.getId(),
                StockMovementType.TRANSFER_OUT,
                MovementDirection.OUT,
                reportUser.getId()
        );
        transferOut.setReferenceType("TRANSFER");
        transferOut.setReferenceId(transferReferenceId);
        transferOut.setNotes("Transfer TRF-2026-0002 to Filial Sul");
        stockMovementRepository.save(transferOut);
        TestDataFactory.createStockMovementItem(
                stockMovementItemRepository,
                transferOut,
                transferProduct,
                destinationBatch,
                java.math.BigDecimal.ONE
        );

        StockMovement transferIn = TestDataFactory.createStockMovement(
                stockMovementRepository,
                testTenant.getId(),
                destinationWarehouse.getId(),
                StockMovementType.TRANSFER_IN,
                MovementDirection.IN,
                reportUser.getId()
        );
        transferIn.setReferenceType("TRANSFER");
        transferIn.setReferenceId(transferReferenceId);
        transferIn.setNotes("Transfer TRF-2026-0002 from Report Warehouse");
        stockMovementRepository.save(transferIn);
        TestDataFactory.createStockMovementItem(
                stockMovementItemRepository,
                transferIn,
                transferProduct,
                destinationBatch,
                java.math.BigDecimal.ONE
        );

        WarehouseContext.setWarehouseId(sourceWarehouse.getId());

        try {
            mockMvc.perform(get("/api/reports/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.recentMovements.length()").value(1))
                    .andExpect(jsonPath("$.data.recentMovements[0].notes")
                            .value("Transfer TRF-2026-0002 to Filial Sul"))
                    .andExpect(jsonPath("$.data.movementStats.today.transfers").value(1));
        } finally {
            WarehouseContext.clear();
        }
    }

    @Test
    @WithMockUser(username = "report@test.com", authorities = { "ROLE_ADMIN" })
    void shouldGetStockReport() throws Exception {
        mockMvc.perform(get("/api/reports/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].productName").value("Report Product"))
                .andExpect(jsonPath("$.data[0].totalQuantity").value(75));
    }

    @Test
    @WithMockUser(username = "report@test.com", authorities = { "ROLE_ADMIN" })
    void shouldGetDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalProducts").exists())
                .andExpect(jsonPath("$.data.totalWarehouses").exists())
                .andExpect(jsonPath("$.data.totalActiveBatches").exists())
                .andExpect(jsonPath("$.data.totalStockQuantity").exists())
                .andExpect(jsonPath("$.data.totalStockValue").exists())
                .andExpect(jsonPath("$.data.totalTransitQuantity").exists())
                .andExpect(jsonPath("$.data.pendingTransfers").exists())
                .andExpect(jsonPath("$.data.todayMovements").exists())
                .andExpect(jsonPath("$.data.criticalAlerts").exists());
    }

    @Test
    @WithMockUser(username = "report@test.com", authorities = { "ROLE_ADMIN" })
    void shouldGetDashboardKpis() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentMonth").exists())
                .andExpect(jsonPath("$.data.currentMonth.totalStockValue").exists())
                .andExpect(jsonPath("$.data.currentMonth.stockTurnoverRate").exists());
    }

    @Test
    @WithMockUser(username = "report@test.com", authorities = { "ROLE_ADMIN" })
    void shouldGetDashboardAlerts() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lowStockProducts").isArray())
                .andExpect(jsonPath("$.data.expiringProducts").isArray())
                .andExpect(jsonPath("$.data.recentLosses").isArray())
                .andExpect(jsonPath("$.data.pendingTransfers").exists())
                .andExpect(jsonPath("$.data.highTransitValue").exists());
    }

    @Test
    @WithMockUser(username = "report@test.com", authorities = { "ROLE_ADMIN" })
    void shouldGetMovementTrend() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard/movement-trend")
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.startDate").exists())
                .andExpect(jsonPath("$.data.endDate").exists())
                .andExpect(jsonPath("$.data.days").isArray())
                .andExpect(jsonPath("$.data.totals").exists())
                .andExpect(jsonPath("$.data.totals.totalInQuantity").exists())
                .andExpect(jsonPath("$.data.totals.totalOutQuantity").exists());
    }
}
