package br.com.stockshift.controller;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.security.WarehouseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorizationScopeIntegrationTest extends BaseIntegrationTest {

    @AfterEach
    void clearWarehouseContext() {
        WarehouseContext.clear();
    }

    @Test
    @WithMockUser(authorities = {"users:create"})
    void shouldReturnForbiddenWhenMissingUsersReadPermission() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"batches:read"})
    void shouldReturnForbiddenWhenWarehouseDiffersFromTokenScope() throws Exception {
        WarehouseContext.setWarehouseId(UUID.randomUUID());

        mockMvc.perform(get("/api/batches/warehouse/{warehouseId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
