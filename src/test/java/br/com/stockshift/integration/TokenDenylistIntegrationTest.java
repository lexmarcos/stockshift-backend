package br.com.stockshift.integration;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.auth.LoginRequest;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.repository.RefreshTokenRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled("Requires Redis")
class TokenDenylistIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUpTestData() {
        // Clear data
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        // Create test data
        Tenant tenant = TestDataFactory.createTenant(tenantRepository, "Test Tenant", "12345678901234");
        TestDataFactory.createUser(userRepository, passwordEncoder, tenant.getId(), "test@test.com");
    }

    @Test
    void afterLogout_accessTokenShouldBeRejected() throws Exception {
        // 1. Login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@test.com");
        loginRequest.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie accessTokenCookie = loginResult.getResponse().getCookie("accessToken");
        Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");

        // 2. Verify access works before logout
        mockMvc.perform(get("/api/warehouses")
                        .cookie(accessTokenCookie))
                .andExpect(status().isOk());

        // 3. Logout
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(accessTokenCookie)
                        .cookie(refreshTokenCookie))
                .andExpect(status().isOk());

        // 4. Verify access is REJECTED after logout
        // This requires Redis to be running and the Denylist logic to be working
        mockMvc.perform(get("/api/warehouses")
                        .cookie(accessTokenCookie))
                .andExpect(status().isUnauthorized());
    }
}
