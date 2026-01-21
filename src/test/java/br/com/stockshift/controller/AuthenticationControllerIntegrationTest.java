package br.com.stockshift.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.auth.LoginRequest;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.RefreshTokenRepository;
import br.com.stockshift.util.TestDataFactory;

class AuthenticationControllerIntegrationTest extends BaseIntegrationTest {

        private ObjectMapper objectMapper = new ObjectMapper();

        @Autowired
        private TenantRepository tenantRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private RefreshTokenRepository refreshTokenRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        private Tenant testTenant;
        private User testUser;

        @BeforeEach
        void setUpTestData() {
                refreshTokenRepository.deleteAll();
                userRepository.deleteAll();
                tenantRepository.deleteAll();

                testTenant = TestDataFactory.createTenant(tenantRepository, "Auth Test Tenant", "11111111000101");
                testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                                testTenant.getId(), "auth@test.com");
        }

        @Test
        void shouldLoginSuccessfully() throws Exception {
                LoginRequest request = new LoginRequest();
                request.setEmail("auth@test.com");
                request.setPassword("password123");

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.email").value("auth@test.com"))
                                .andExpect(jsonPath("$.data.accessToken").doesNotExist()) // Should not be in JSON
                                .andExpect(jsonPath("$.data.refreshToken").doesNotExist()) // Should not be in JSON
                                .andExpect(cookie().exists("accessToken"))
                                .andExpect(cookie().exists("refreshToken"))
                                .andExpect(cookie().httpOnly("accessToken", true))
                                .andExpect(cookie().httpOnly("refreshToken", true))
                                .andExpect(cookie().path("accessToken", "/"))
                                .andExpect(cookie().path("refreshToken", "/"));
        }

        @Test
        void shouldRefreshToken() throws Exception {
                // First, login to get cookies
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail("auth@test.com");
                loginRequest.setPassword("password123");

                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn();

                Cookie accessTokenCookie = loginResult.getResponse().getCookie("accessToken");
                Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");

                assertNotNull(refreshTokenCookie);

                // Now test refresh using cookies
                mockMvc.perform(post("/api/auth/refresh")
                                .cookie(refreshTokenCookie))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(cookie().exists("accessToken"))
                                .andExpect(cookie().exists("refreshToken"));
        }

        @Test
        void shouldLogoutSuccessfully() throws Exception {
                // First, login to get cookies
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail("auth@test.com");
                loginRequest.setPassword("password123");

                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn();

                Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");
                assertNotNull(refreshTokenCookie);

                // Now test logout
                MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                                .cookie(refreshTokenCookie))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andReturn();

                // Check that cookies are cleared (Max-Age=0)
                Cookie clearedAccessToken = logoutResult.getResponse().getCookie("accessToken");
                Cookie clearedRefreshToken = logoutResult.getResponse().getCookie("refreshToken");

                assertNotNull(clearedAccessToken);
                assertNotNull(clearedRefreshToken);
                assertEquals(0, clearedAccessToken.getMaxAge());
                assertEquals(0, clearedRefreshToken.getMaxAge());
        }
}
