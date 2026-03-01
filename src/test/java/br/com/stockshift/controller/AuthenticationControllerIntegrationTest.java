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
import br.com.stockshift.dto.auth.SwitchWarehouseRequest;
import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.UserRoleWarehouse;
import br.com.stockshift.model.entity.UserRoleWarehouseId;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.PermissionRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.RefreshTokenRepository;
import br.com.stockshift.repository.RoleRepository;
import br.com.stockshift.repository.UserRoleWarehouseRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.JwtTokenProvider;
import br.com.stockshift.util.TestDataFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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

        @Autowired
        private RoleRepository roleRepository;

        @Autowired
        private PermissionRepository permissionRepository;

        @Autowired
        private WarehouseRepository warehouseRepository;

        @Autowired
        private UserRoleWarehouseRepository userRoleWarehouseRepository;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        private Tenant testTenant;
        private User testUser;
        private Warehouse primaryWarehouse;
        private Role operatorRole;

        @BeforeEach
        void setUpTestData() {
                refreshTokenRepository.deleteAll();
                userRoleWarehouseRepository.deleteAll();
                userRepository.deleteAll();
                roleRepository.deleteAll();
                warehouseRepository.deleteAll();
                tenantRepository.deleteAll();

                testTenant = TestDataFactory.createTenant(tenantRepository, "Auth Test Tenant", "11111111000101");
                testUser = TestDataFactory.createUser(userRepository, passwordEncoder,
                                testTenant.getId(), "auth@test.com");

                primaryWarehouse = TestDataFactory.createWarehouse(warehouseRepository, testTenant.getId(), "Primary");

                Permission usersRead = permissionRepository.findByCodeIgnoreCase("users:read")
                                .orElseThrow(() -> new IllegalStateException("users:read permission not found"));

                operatorRole = new Role();
                operatorRole.setTenantId(testTenant.getId());
                operatorRole.setName("OPERATOR");
                operatorRole.setDescription("Operator role for auth integration tests");
                operatorRole.setIsSystemRole(false);
                operatorRole.setPermissions(new HashSet<>(Set.of(usersRead)));
                operatorRole = roleRepository.save(operatorRole);

                UserRoleWarehouse assignment = new UserRoleWarehouse();
                assignment.setId(new UserRoleWarehouseId(testUser.getId(), operatorRole.getId(),
                                primaryWarehouse.getId()));
                assignment.setUser(testUser);
                assignment.setRole(operatorRole);
                assignment.setWarehouse(primaryWarehouse);
                userRoleWarehouseRepository.save(assignment);
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

                assertNotNull(accessTokenCookie);
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

                Cookie accessTokenCookie = loginResult.getResponse().getCookie("accessToken");
                Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");
                assertNotNull(accessTokenCookie);
                assertNotNull(refreshTokenCookie);

                // Now test logout
                MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                                .cookie(accessTokenCookie)
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

        @Test
        void shouldIncludeWarehouseAndAuthoritiesClaimsOnLogin() throws Exception {
                LoginRequest request = new LoginRequest();
                request.setEmail("auth@test.com");
                request.setPassword("password123");

                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn();

                Cookie accessTokenCookie = loginResult.getResponse().getCookie("accessToken");
                assertNotNull(accessTokenCookie);

                UUID warehouseId = jwtTokenProvider.getWarehouseIdFromToken(accessTokenCookie.getValue());
                assertEquals(primaryWarehouse.getId(), warehouseId);
                assertTrue(jwtTokenProvider.getAuthoritiesFromToken(accessTokenCookie.getValue())
                                .contains("users:read"));
        }

        @Test
        void shouldSwitchWarehouseOnlyWhenUserHasAssignment() throws Exception {
                Warehouse secondaryWarehouse = TestDataFactory.createWarehouse(
                                warehouseRepository,
                                testTenant.getId(),
                                "Secondary");

                UserRoleWarehouse assignment = new UserRoleWarehouse();
                assignment.setId(new UserRoleWarehouseId(testUser.getId(), operatorRole.getId(),
                                secondaryWarehouse.getId()));
                assignment.setUser(testUser);
                assignment.setRole(operatorRole);
                assignment.setWarehouse(secondaryWarehouse);
                userRoleWarehouseRepository.save(assignment);

                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail("auth@test.com");
                loginRequest.setPassword("password123");

                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn();

                Cookie accessTokenCookie = loginResult.getResponse().getCookie("accessToken");
                assertNotNull(accessTokenCookie);

                SwitchWarehouseRequest allowedRequest = new SwitchWarehouseRequest(secondaryWarehouse.getId());
                MvcResult allowedResult = mockMvc.perform(post("/api/auth/switch-warehouse")
                                .cookie(accessTokenCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(allowedRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                Cookie switchedAccessToken = allowedResult.getResponse().getCookie("accessToken");
                assertNotNull(switchedAccessToken);
                assertEquals(
                                secondaryWarehouse.getId(),
                                jwtTokenProvider.getWarehouseIdFromToken(switchedAccessToken.getValue()));

                SwitchWarehouseRequest forbiddenRequest = new SwitchWarehouseRequest(UUID.randomUUID());
                mockMvc.perform(post("/api/auth/switch-warehouse")
                                .cookie(switchedAccessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(forbiddenRequest)))
                                .andExpect(status().isForbidden());
        }
}
