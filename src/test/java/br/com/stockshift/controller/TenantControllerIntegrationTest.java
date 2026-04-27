package br.com.stockshift.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.StorageService;
import br.com.stockshift.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

class TenantControllerIntegrationTest extends BaseIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private StorageService storageService;

    private Tenant testTenant;

    @BeforeEach
    void setUpTestData() {
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        testTenant = TestDataFactory.createTenant(tenantRepository, "Tenant Test", "44444444000104");
        TestDataFactory.createUser(userRepository, passwordEncoder, testTenant.getId(), "tenant@test.com");
        TenantContext.setTenantId(testTenant.getId());
    }

    @Test
    @WithMockUser(username = "tenant@test.com", authorities = { "ROLE_ADMIN" })
    void shouldReturnCompanyLogoUrl() throws Exception {
        testTenant.setLogoUrl("https://cdn.example.com/company-logos/logo.png");
        tenantRepository.save(testTenant);

        mockMvc.perform(get("/api/tenants/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoUrl").value("https://cdn.example.com/company-logos/logo.png"));
    }

    @Test
    @WithMockUser(username = "tenant@test.com", authorities = { "ROLE_ADMIN" })
    void shouldUpdateCompanyConfigWithJsonBody() throws Exception {
        UpdateCompanyPayload request = new UpdateCompanyPayload(
                "New Business",
                "55555555000105",
                "new@tenant.com",
                "85999999999");

        mockMvc.perform(put("/api/tenants/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessName").value("New Business"));
    }

    @Test
    @WithMockUser(username = "tenant@test.com", authorities = { "ROLE_ADMIN" })
    void shouldUpdateCompanyConfigWithLogo() throws Exception {
        when(storageService.uploadCompanyLogo(any(MultipartFile.class)))
                .thenReturn("https://cdn.example.com/company-logos/new-logo.svg");

        MockMultipartFile companyPart = new MockMultipartFile(
                "company",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(new UpdateCompanyPayload(
                        "Logo Business",
                        "66666666000106",
                        "logo@tenant.com",
                        "85888888888")));
        MockMultipartFile logoPart = new MockMultipartFile(
                "logo",
                "logo.svg",
                "image/svg+xml",
                "<svg />".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/tenants/me")
                .file(companyPart)
                .file(logoPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessName").value("Logo Business"))
                .andExpect(jsonPath("$.data.logoUrl").value("https://cdn.example.com/company-logos/new-logo.svg"));

        verify(storageService).uploadCompanyLogo(any(MultipartFile.class));
    }

    private record UpdateCompanyPayload(
            String businessName,
            String document,
            String email,
            String phone) {
    }
}
