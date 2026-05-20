package br.com.stockshift.controller;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.dto.productprompt.ProductPromptRequest;
import br.com.stockshift.model.entity.ProductPrompt;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.repository.ProductPromptRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductPromptControllerIntegrationTest extends BaseIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ProductPromptRepository productPromptRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @MockitoBean
    private StorageService storageService;

    private Tenant tenant;

    @BeforeEach
    void setUpTestData() {
        productPromptRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant = new Tenant();
        tenant.setBusinessName("Product Prompt Tenant");
        tenant.setDocument("22222222000122");
        tenant.setEmail("prompts@tenant.com");
        tenant.setIsActive(true);
        tenant = tenantRepository.save(tenant);
        TenantContext.setTenantId(tenant.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @WithMockUser(username = "manager@test.com", authorities = {
            "product_prompts:create",
            "product_prompts:read",
            "product_prompts:update",
            "product_prompts:delete"
    })
    void shouldCreateListUpdateAndDeleteProductPromptWithOwnPermissions() throws Exception {
        when(storageService.uploadImage(any())).thenReturn("https://cdn.example.com/prompt.png");

        String createdId = objectMapper.readTree(mockMvc.perform(multipart("/api/product-prompts")
                        .file(promptPart(ProductPromptRequest.builder()
                                .name("Oferta premium")
                                .prompt("Crie uma arte premium")
                                .build()))
                        .file(imagePart()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Oferta premium"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/prompt.png"))
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(get("/api/product-prompts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/product-prompts/{id}", UUID.fromString(createdId))
                        .file(promptPart(ProductPromptRequest.builder()
                                .name("Oferta atualizada")
                                .prompt("Prompt atualizado")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Oferta atualizada"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/prompt.png"));

        mockMvc.perform(delete("/api/product-prompts/{id}", UUID.fromString(createdId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ProductPrompt deleted = productPromptRepository.findById(UUID.fromString(createdId)).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    @WithMockUser(username = "reader@test.com", authorities = { "product_prompts:read" })
    void shouldListOnlyCurrentTenantPrompts() throws Exception {
        tenant.setLogoUrl("https://pub-test.r2.dev/company-logos/logo.png");
        tenantRepository.save(tenant);
        createPrompt(tenant.getId(), "Tenant atual");
        Tenant otherTenant = createTenant("Other Tenant", "other@tenant.com", "33333333000133");
        createPrompt(otherTenant.getId(), "Outro tenant");

        mockMvc.perform(get("/api/product-prompts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Tenant atual"));

        mockMvc.perform(get("/api/product-prompts/company-assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoUrl")
                        .value("https://pub-test.r2.dev/company-logos/logo.png"));
    }

    @Test
    @WithMockUser(username = "writer@test.com", authorities = { "product_prompts:create" })
    void shouldReturnForbiddenWhenMissingReadPermission() throws Exception {
        mockMvc.perform(get("/api/product-prompts"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/product-prompts/company-assets"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "writer@test.com", authorities = { "product_prompts:create" })
    void shouldRequireImageOnCreate() throws Exception {
        mockMvc.perform(multipart("/api/product-prompts")
                        .file(promptPart(ProductPromptRequest.builder()
                                .name("Sem imagem")
                                .prompt("Prompt")
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Prompt image is required"));
    }

    private MockMultipartFile promptPart(ProductPromptRequest request) throws Exception {
        return new MockMultipartFile(
                "prompt",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request));
    }

    private MockMultipartFile imagePart() {
        return new MockMultipartFile("image", "prompt.png", "image/png", new byte[] { 1 });
    }

    private ProductPrompt createPrompt(UUID tenantId, String name) {
        ProductPrompt prompt = new ProductPrompt();
        prompt.setTenantId(tenantId);
        prompt.setName(name);
        prompt.setPrompt("Prompt");
        prompt.setImageUrl("https://cdn.example.com/" + UUID.randomUUID() + ".png");
        prompt.setCreatedAt(LocalDateTime.now());
        prompt.setUpdatedAt(LocalDateTime.now());
        return productPromptRepository.save(prompt);
    }

    private Tenant createTenant(String businessName, String email, String document) {
        Tenant newTenant = new Tenant();
        newTenant.setBusinessName(businessName);
        newTenant.setEmail(email);
        newTenant.setDocument(document);
        newTenant.setIsActive(true);
        return tenantRepository.save(newTenant);
    }
}
