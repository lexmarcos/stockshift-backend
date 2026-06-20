package br.com.stockshift.controller.internal;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BotWarehouseControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BOT_KEY = "test-bot-key";
    private static final UUID BOT_TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @BeforeEach
    void setUpData() {
        warehouseRepository.deleteAll();
        tenantRepository.deleteAll();
        createTenant("Bot Tenant", "11111111000111");
        createTenant("Other Tenant", "22222222000122");
    }

    @Test
    void shouldListOnlyActiveBotTenantWarehouses() throws Exception {
        TestDataFactory.createWarehouse(warehouseRepository, BOT_TENANT_ID, "Centro");
        Warehouse inactive = TestDataFactory.createWarehouse(warehouseRepository, BOT_TENANT_ID, "Inativo");
        inactive.setIsActive(false);
        warehouseRepository.save(inactive);
        TestDataFactory.createWarehouse(warehouseRepository, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "Outro Tenant");

        mockMvc.perform(get("/api/internal/bot/warehouses")
                        .header("X-StockShift-Bot-Key", BOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Centro"));
    }

    @Test
    void shouldSearchActiveWarehousesByNameOrCode() throws Exception {
        Warehouse center = TestDataFactory.createWarehouse(warehouseRepository, BOT_TENANT_ID, "Deposito Centro");
        center.setCode("CTR");
        warehouseRepository.save(center);
        TestDataFactory.createWarehouse(warehouseRepository, BOT_TENANT_ID, "Zona Norte");

        mockMvc.perform(get("/api/internal/bot/warehouses/search")
                        .param("query", "ctr")
                        .header("X-StockShift-Bot-Key", BOT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Deposito Centro"))
                .andExpect(jsonPath("$.data[0].code").value("CTR"));
    }

    @Test
    void shouldRejectWarehouseRouteWithoutBotKey() throws Exception {
        mockMvc.perform(get("/api/internal/bot/warehouses"))
                .andExpect(status().isUnauthorized());
    }

    private Tenant createTenant(String name, String document) {
        Tenant tenant = new Tenant();
        tenant.setBusinessName(name);
        tenant.setDocument(document);
        tenant.setEmail(document + "@test.com");
        tenant.setIsActive(true);
        return tenantRepository.saveAndFlush(tenant);
    }
}
