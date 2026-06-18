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
import br.com.stockshift.model.entity.RefreshToken;
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

import java.time.LocalDateTime;
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

        // Regression for the concurrent-refresh logout: on mobile, several requests
        // fire after the access token expires and each carries the same refresh cookie.
        // The second one still holds the pre-rotation token; it must stay valid within
        // the grace window instead of returning 401 and forcing a re-login.
        @Test
        void shouldKeepSessionAliveWhenSameRefreshTokenIsUsedConcurrently() throws Exception {
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail("auth@test.com");
                loginRequest.setPassword("password123");

                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn();

                Cookie originalRefreshCookie = loginResult.getResponse().getCookie("refreshToken");
                assertNotNull(originalRefreshCookie);

                // First refresh rotates the original token.
                MvcResult firstRefresh = mockMvc.perform(post("/api/auth/refresh")
                                .cookie(originalRefreshCookie))
                                .andExpect(status().isOk())
                                .andReturn();
                Cookie rotatedRefreshCookie = firstRefresh.getResponse().getCookie("refreshToken");
                assertNotNull(rotatedRefreshCookie);
                assertNotEquals(originalRefreshCookie.getValue(), rotatedRefreshCookie.getValue());

                // Second (concurrent) refresh still presents the ORIGINAL cookie.
                // Before the grace-period fix this returned 401 and logged the user out.
                mockMvc.perform(post("/api/auth/refresh")
                                .cookie(originalRefreshCookie))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(cookie().exists("accessToken"))
                                .andExpect(cookie().exists("refreshToken"));

                // The token issued by the first refresh must remain usable too.
                mockMvc.perform(post("/api/auth/refresh")
                                .cookie(rotatedRefreshCookie))
                                .andExpect(status().isOk());
        }

        // Regression for the codex review: logout must revoke the WHOLE session, not
        // just the presented refresh token. Otherwise a concurrent-refresh sibling
        // stays valid and could silently restore the session after logout.
        @Test
        void shouldRevokeEveryRefreshTokenOfTheSessionOnLogout() throws Exception {
                MvcResult loginResult = performLogin();
                Cookie accessTokenCookie = loginResult.getResponse().getCookie("accessToken");
                Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");
                assertNotNull(accessTokenCookie);
                assertNotNull(refreshTokenCookie);

                // A still-valid sibling left over from a concurrent refresh.
                RefreshToken sibling = persistSiblingToken(LocalDateTime.now());
                assertTrue(refreshTokenRepository.findByToken(sibling.getToken()).isPresent());

                mockMvc.perform(post("/api/auth/logout")
                                .cookie(accessTokenCookie)
                                .cookie(refreshTokenCookie))
                                .andExpect(status().isOk());

                assertTrue(refreshTokenRepository.findByToken(sibling.getToken()).isEmpty());
                assertTrue(refreshTokenRepository.findByToken(refreshTokenCookie.getValue()).isEmpty());
        }

        // Abandoned siblings (unrotated, older than the grace window) must be purged
        // on the next refresh instead of lingering for the full refresh-token lifetime.
        @Test
        void shouldPurgeAbandonedSiblingsOlderThanGraceOnRefresh() throws Exception {
                MvcResult loginResult = performLogin();
                Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");
                assertNotNull(refreshTokenCookie);

                RefreshToken stale = persistSiblingToken(LocalDateTime.now().minusMinutes(5));

                mockMvc.perform(post("/api/auth/refresh")
                                .cookie(refreshTokenCookie))
                                .andExpect(status().isOk());

                assertTrue(refreshTokenRepository.findByToken(stale.getToken()).isEmpty());
        }

        // Security regression (codex review): replaying the pre-rotation cookie within
        // the grace window must return the already-issued successor, never mint a fresh
        // long-lived token, so a stolen/duplicated old cookie can't bootstrap a session.
        @Test
        void shouldReturnTrackedSuccessorWhenRotatedCookieIsReplayedWithinGrace() throws Exception {
                MvcResult loginResult = performLogin();
                Cookie originalRefreshCookie = loginResult.getResponse().getCookie("refreshToken");
                assertNotNull(originalRefreshCookie);

                MvcResult firstRefresh = mockMvc.perform(post("/api/auth/refresh")
                                .cookie(originalRefreshCookie))
                                .andExpect(status().isOk())
                                .andReturn();
                Cookie successorCookie = firstRefresh.getResponse().getCookie("refreshToken");
                assertNotNull(successorCookie);
                long tokensAfterFirstRefresh = refreshTokenRepository.count();

                // Replay the original (now-rotated) cookie while still inside the grace window.
                MvcResult replay = mockMvc.perform(post("/api/auth/refresh")
                                .cookie(originalRefreshCookie))
                                .andExpect(status().isOk())
                                .andReturn();
                Cookie replayCookie = replay.getResponse().getCookie("refreshToken");
                assertNotNull(replayCookie);

                // Same successor handed back, and no extra token minted.
                assertEquals(successorCookie.getValue(), replayCookie.getValue());
                assertEquals(tokensAfterFirstRefresh, refreshTokenRepository.count());
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

        private MvcResult performLogin() throws Exception {
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail("auth@test.com");
                loginRequest.setPassword("password123");
                return mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn();
        }

        // Persists an extra valid refresh token for the test user, simulating a token
        // left behind by a concurrent refresh. saveAndFlush so it hits the DB before
        // the endpoint under test runs its bulk cleanup/revocation queries.
        private RefreshToken persistSiblingToken(LocalDateTime createdAt) {
                RefreshToken sibling = new RefreshToken();
                sibling.setToken(UUID.randomUUID().toString());
                sibling.setUser(testUser);
                sibling.setWarehouseId(primaryWarehouse.getId());
                sibling.setExpiresAt(LocalDateTime.now().plusDays(7));
                sibling.setCreatedAt(createdAt);
                return refreshTokenRepository.saveAndFlush(sibling);
        }
}
