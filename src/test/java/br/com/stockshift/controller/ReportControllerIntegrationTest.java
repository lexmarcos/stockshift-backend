package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.TenantContext;
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
