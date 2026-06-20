package br.com.stockshift.controller.internal;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BotProductControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BOT_KEY = "test-bot-key";
    private static final UUID BOT_TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Warehouse warehouse;
    private Category category;

    @BeforeEach
    void setUpData() {
        batchRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        warehouseRepository.deleteAllInBatch();
        tenantRepository.deleteAllInBatch();
        createTenant(BOT_TENANT_ID, "Bot Tenant", "33333333000133");
        warehouse = TestDataFactory.createWarehouse(warehouseRepository, BOT_TENANT_ID, "Centro");
        category = TestDataFactory.createCategory(categoryRepository, BOT_TENANT_ID, "Perfumes");
    }

    @Test
    void shouldSearchProductByNameAndReturnStockAndLatestBatchPrice() throws Exception {
        Product product = TestDataFactory.createProduct(productRepository, BOT_TENANT_ID, category, "Perfume Gold", "SKU-GOLD");
        product.setImageUrl("https://cdn.example.com/products/gold.png");
        product.setBarcode("7891234567890");
        productRepository.save(product);
        Batch oldBatch = TestDataFactory.createBatch(batchRepository, BOT_TENANT_ID, product, warehouse, "OLD", 10);
        oldBatch.setSellingPrice(1000L);
        batchRepository.saveAndFlush(oldBatch);
        setCreatedAt(oldBatch.getId(), LocalDateTime.parse("2026-01-01T10:00:00"));
        Batch newBatch = TestDataFactory.createBatch(batchRepository, BOT_TENANT_ID, product, warehouse, "NEW", 15);
        newBatch.setSellingPrice(12990L);
        batchRepository.saveAndFlush(newBatch);
        setCreatedAt(newBatch.getId(), LocalDateTime.parse("2026-02-01T10:00:00"));

        mockMvc.perform(get("/api/internal/bot/products/search")
                        .param("query", "gold")
                        .param("warehouseId", warehouse.getId().toString())
                        .header("X-StockShift-Bot-Key", BOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.results.length()").value(1))
                .andExpect(jsonPath("$.data.results[0].name").value("Perfume Gold"))
                .andExpect(jsonPath("$.data.results[0].imageUrl").value("https://cdn.example.com/products/gold.png"))
                .andExpect(jsonPath("$.data.results[0].barcode").value("7891234567890"))
                .andExpect(jsonPath("$.data.results[0].totalQuantity").value(25))
                .andExpect(jsonPath("$.data.results[0].latestBatchSellingPrice").value(12990))
                .andExpect(jsonPath("$.data.results[0].latestBatchCode").value("NEW"));
    }

    @Test
    void shouldSearchProductBySkuAndBarcode() throws Exception {
        Product product = TestDataFactory.createProduct(productRepository, BOT_TENANT_ID, category, "Body Splash", "SKU-BODY");
        product.setBarcode("9998887776665");
        productRepository.save(product);
        TestDataFactory.createBatch(batchRepository, BOT_TENANT_ID, product, warehouse, "BODY", 3);

        mockMvc.perform(get("/api/internal/bot/products/search")
                        .param("query", "9998887776665")
                        .param("warehouseId", warehouse.getId().toString())
                        .header("X-StockShift-Bot-Key", BOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].sku").value("SKU-BODY"));
    }

    @Test
    void shouldReturnHasMoreWhenMoreResultsExistThanLimit() throws Exception {
        for (int index = 1; index <= 6; index++) {
            Product product = TestDataFactory.createProduct(productRepository, BOT_TENANT_ID, category,
                    "Perfume Match " + index, "SKU-MATCH-" + index);
            TestDataFactory.createBatch(batchRepository, BOT_TENANT_ID, product, warehouse, "BATCH-" + index, 1);
        }

        mockMvc.perform(get("/api/internal/bot/products/search")
                        .param("query", "Perfume Match")
                        .param("warehouseId", warehouse.getId().toString())
                        .param("limit", "5")
                        .header("X-StockShift-Bot-Key", BOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(5))
                .andExpect(jsonPath("$.data.hasMore").value(true));
    }

    @Test
    void shouldReturnEmptyResultsForNoMatch() throws Exception {
        mockMvc.perform(get("/api/internal/bot/products/search")
                        .param("query", "not-found")
                        .param("warehouseId", warehouse.getId().toString())
                        .header("X-StockShift-Bot-Key", BOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(0))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    private void createTenant(UUID tenantId, String name, String document) {
        jdbcTemplate.update("""
                INSERT INTO tenants (id, business_name, document, email, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, true, NOW(), NOW())
                """,
                tenantId, name, document, document + "@test.com");
    }

    private void setCreatedAt(UUID batchId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE batches SET created_at = ? WHERE id = ?", Timestamp.valueOf(createdAt), batchId);
    }
}
