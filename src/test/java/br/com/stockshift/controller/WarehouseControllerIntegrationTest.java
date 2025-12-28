package br.com.stockshift.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.warehouse.WarehouseRequest;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.TestDataFactory;

class WarehouseControllerIntegrationTest extends BaseIntegrationTest {

    private ObjectMapper objectMapper = new ObjectMapper();

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

    @BeforeEach
    void setUpTestData() {
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Warehouse Test Tenant", "33333333000103");
        testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                testTenant.getId(), "warehouse@test.com");

        TenantContext.setTenantId(testTenant.getId());
    }

    @Test
    @WithMockUser(username = "warehouse@test.com", authorities = {"ROLE_ADMIN"})
    void shouldCreateWarehouse() throws Exception {
        WarehouseRequest request = WarehouseRequest.builder()
                .name("Main Warehouse")
                .address("123 Storage St")
                .city("São Paulo")
                .state("SP")
                .isActive(true)
                .build();

        mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Main Warehouse"))
                .andExpect(jsonPath("$.data.address").value("123 Storage St"));
    }

    @Test
    @WithMockUser(username = "warehouse@test.com", authorities = {"ROLE_ADMIN"})
    void shouldGetWarehouseById() throws Exception {
        Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository,
                testTenant.getId(), "Storage Unit A");

        mockMvc.perform(get("/api/warehouses/{id}", warehouse.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(warehouse.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Storage Unit A"));
    }

    @Test
    @WithMockUser(username = "warehouse@test.com", authorities = {"ROLE_ADMIN"})
    void shouldListAllWarehouses() throws Exception {
        TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Warehouse 1");
        TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Warehouse 2");

        mockMvc.perform(get("/api/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}
