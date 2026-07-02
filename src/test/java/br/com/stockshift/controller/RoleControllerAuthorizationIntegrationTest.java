package br.com.stockshift.controller;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.repository.RoleRepository;
import br.com.stockshift.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleControllerAuthorizationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @WithMockUser(authorities = {"roles:read"})
    void shouldReturnRolesForUserWithRolesRead() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        roleRepository.save(role(tenantId, "Reader"));

        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Reader"));
    }

    @Test
    @WithMockUser(authorities = {"products:read"})
    void shouldRejectRolesForUserWithoutRolesRead() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isForbidden());
    }

    private Role role(UUID tenantId, String name) {
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setName(name);
        role.setDescription(name + " role");
        role.setIsSystemRole(false);
        return role;
    }
}
