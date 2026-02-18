package br.com.stockshift.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import br.com.stockshift.BaseIntegrationTest;

@Transactional
class PermissionControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN"})
    void shouldReturnSeededPermissionsForAdmin() throws Exception {
        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[*].resource", hasItem("PRODUCT")))
                .andExpect(jsonPath("$.data[*].action", hasItem("READ")))
                .andExpect(jsonPath("$.data[*].scope", hasItem("ALL")));
    }
}
