package br.com.stockshift.security;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.Sale;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.UserRoleWarehouse;
import br.com.stockshift.model.entity.UserRoleWarehouseId;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.PaymentMode;
import br.com.stockshift.model.enums.SaleStatus;
import br.com.stockshift.repository.PermissionRepository;
import br.com.stockshift.repository.RefreshTokenRepository;
import br.com.stockshift.repository.RoleRepository;
import br.com.stockshift.repository.SaleRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.UserRoleWarehouseRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.ratelimit.RateLimitFilter;
import br.com.stockshift.security.ratelimit.RateLimitService;
import br.com.stockshift.service.sale.InfinitePayCheckoutService;
import br.com.stockshift.util.TestDataFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityAuditRegressionTest extends BaseIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRoleWarehouseRepository userRoleWarehouseRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private InfinitePayCheckoutService infinitePayCheckoutService;

    @BeforeEach
    void resetSecurityAuditData() {
        reset(infinitePayCheckoutService);
        saleRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRoleWarehouseRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        warehouseRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void shouldRejectUnauthenticatedInfinitePayConfirmBeforeChangingSaleStatus() throws Exception {
        AuthFixture fixture = createAssignedUser("sales:read");
        Sale sale = createPendingSale(fixture);

        MvcResult result = mockMvc.perform(get("/api/sales/infinitepay/confirm")
                .param("order_id", sale.getId().toString())
                .param("nsu", "forged-nsu")
                .param("aut", "forged-auth")
                .param("card_brand", "visa"))
                .andReturn();

        assertThat(reloadSaleStatus(sale)).isEqualTo(SaleStatus.PENDING);
        assertThat(result.getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    void shouldRejectForgedInfinitePayWebhookBeforeChangingSaleStatus() throws Exception {
        AuthFixture fixture = createAssignedUser("sales:read");
        Sale sale = createPendingSale(fixture);

        MvcResult result = mockMvc.perform(post("/api/sales/infinitepay/webhook/forged-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"order_nsu":"%s","transaction_nsu":"forged-txn","capture_method":"pix","invoice_slug":"fake","receipt_url":"https://evil.example/receipt","installments":1}
                        """.formatted(sale.getId())))
                .andReturn();

        assertThat(reloadSaleStatus(sale)).isEqualTo(SaleStatus.PENDING);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(infinitePayCheckoutService);
    }

    @Test
    void shouldConfirmTokenizedInfinitePayWebhookAfterPaymentCheck() throws Exception {
        AuthFixture fixture = createAssignedUser("sales:read");
        fixture.tenant().setInfinitepayHandle("merchant");
        tenantRepository.saveAndFlush(fixture.tenant());
        Sale sale = createPendingSale(fixture);
        sale.setInfinitepayInvoiceSlug("slug-123");
        sale.setInfinitepayWebhookTokenHash(hashWebhookToken("secret-token"));
        saleRepository.saveAndFlush(sale);
        InfinitePayCheckoutService.PaymentCheckResponse paymentCheck = paidPaymentCheck();
        when(infinitePayCheckoutService.checkPayment("merchant", sale.getId().toString(), "txn", "slug-123"))
                .thenReturn(paymentCheck);

        mockMvc.perform(post("/api/sales/infinitepay/webhook/secret-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"order_nsu":"%s","transaction_nsu":"txn","capture_method":"pix","invoice_slug":"slug-123","receipt_url":"https://pay.example/receipt","installments":1}
                        """.formatted(sale.getId())))
                .andExpect(status().isOk());

        Sale reloaded = saleRepository.findById(sale.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(reloaded.getInfinitepayNsu()).isEqualTo("txn");
        verify(infinitePayCheckoutService).checkPayment("merchant", sale.getId().toString(), "txn", "slug-123");
    }

    @Test
    void shouldForbidTenantInfinitePayUpdateWithoutTenantAdminPermission() throws Exception {
        AuthFixture fixture = createAssignedUser("users:read");
        Cookie accessToken = login(fixture.user().getEmail());

        mockMvc.perform(put("/api/tenants/me/infinitepay")
                .cookie(accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"handle\":\"attacker-handle\",\"docNumber\":\"99999999000199\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectAccessTokenAfterUserIsDisabled() throws Exception {
        AuthFixture fixture = createAssignedUser("users:read");
        Cookie accessToken = login(fixture.user().getEmail());
        fixture.user().setIsActive(false);
        userRepository.saveAndFlush(fixture.user());

        MvcResult result = mockMvc.perform(get("/api/users").cookie(accessToken))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    void shouldReevaluatePermissionsAfterRolePermissionIsRemoved() throws Exception {
        AuthFixture fixture = createAssignedUser("users:read");
        Cookie accessToken = login(fixture.user().getEmail());
        fixture.role().getPermissions().clear();
        roleRepository.saveAndFlush(fixture.role());

        mockMvc.perform(get("/api/users").cookie(accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldUseConnectionAddressForLoginRateLimitWhenProxyIsUntrusted() throws Exception {
        RateLimitService rateLimitService = mock(RateLimitService.class);
        when(rateLimitService.tryConsume(anyString())).thenReturn(true);

        RateLimitFilter filter = new RateLimitFilter(rateLimitService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/stockshift/api/auth/login");
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.77");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(rateLimitService).tryConsume("203.0.113.10");
        verify(filterChain).doFilter(request, response);
    }

    private AuthFixture createAssignedUser(String... permissionCodes) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = TestDataFactory.createTenant(tenantRepository, "Audit Tenant " + suffix, suffix);
        User user = TestDataFactory.createUser(userRepository, passwordEncoder, tenant.getId(),
                "audit-" + suffix + "@test.com");
        Warehouse warehouse = TestDataFactory.createWarehouse(warehouseRepository, tenant.getId(), "Audit WH " + suffix);
        Role role = createRole(tenant.getId(), "AUDIT_ROLE_" + suffix, permissionCodes);
        assignRoleWarehouse(user, role, warehouse);
        return new AuthFixture(tenant, user, warehouse, role);
    }

    private Role createRole(UUID tenantId, String name, String... permissionCodes) {
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setName(name);
        role.setDescription("Security audit regression role");
        role.setIsSystemRole(false);
        role.setPermissions(resolvePermissions(permissionCodes));
        return roleRepository.saveAndFlush(role);
    }

    private Set<Permission> resolvePermissions(String... permissionCodes) {
        Set<Permission> permissions = new HashSet<>();
        for (String code : permissionCodes) {
            permissions.add(permissionRepository.findByCodeIgnoreCase(code)
                    .orElseThrow(() -> new IllegalStateException("Permission not found: " + code)));
        }
        return permissions;
    }

    private void assignRoleWarehouse(User user, Role role, Warehouse warehouse) {
        UserRoleWarehouse assignment = new UserRoleWarehouse();
        assignment.setId(new UserRoleWarehouseId(user.getId(), role.getId(), warehouse.getId()));
        assignment.setUser(user);
        assignment.setRole(role);
        assignment.setWarehouse(warehouse);
        userRoleWarehouseRepository.saveAndFlush(assignment);
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"password123"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie accessToken = result.getResponse().getCookie("accessToken");
        assertThat(accessToken).isNotNull();
        return accessToken;
    }

    private Sale createPendingSale(AuthFixture fixture) {
        Sale sale = Sale.builder()
                .code("SEC-" + UUID.randomUUID().toString().substring(0, 8))
                .warehouseId(fixture.warehouse().getId())
                .paymentMethod(PaymentMethod.PIX)
                .subtotal(1000L)
                .discountAmount(0L)
                .total(1000L)
                .status(SaleStatus.PENDING)
                .paymentMode(PaymentMode.LINK)
                .createdByUserId(fixture.user().getId())
                .build();
        sale.setTenantId(fixture.tenant().getId());
        return saleRepository.saveAndFlush(sale);
    }

    private InfinitePayCheckoutService.PaymentCheckResponse paidPaymentCheck() {
        InfinitePayCheckoutService.PaymentCheckResponse response =
                new InfinitePayCheckoutService.PaymentCheckResponse();
        response.setSuccess(true);
        response.setPaid(true);
        response.setAmount(1000L);
        response.setPaidAmount(1010L);
        response.setInstallments(1);
        response.setCaptureMethod("pix");
        return response;
    }

    private String hashWebhookToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable in test runtime", e);
        }
    }

    private SaleStatus reloadSaleStatus(Sale sale) {
        return saleRepository.findById(sale.getId()).orElseThrow().getStatus();
    }

    private record AuthFixture(Tenant tenant, User user, Warehouse warehouse, Role role) {
    }
}
