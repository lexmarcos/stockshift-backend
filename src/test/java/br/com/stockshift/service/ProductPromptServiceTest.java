package br.com.stockshift.service;

import br.com.stockshift.dto.productprompt.ProductPromptRequest;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.ProductPrompt;
import br.com.stockshift.repository.ProductPromptRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductPromptServiceTest {

    @Mock
    private ProductPromptRepository productPromptRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private StorageService storageService;

    private ProductPromptService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        service = new ProductPromptService(productPromptRepository, tenantRepository);
        ReflectionTestUtils.setField(service, "storageService", storageService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void findAllShouldUseCurrentTenant() {
        ProductPrompt productPrompt = productPrompt("Oferta", "Prompt", "https://cdn.example.com/prompt.png");
        when(productPromptRepository.findAllActiveByTenantId(tenantId)).thenReturn(List.of(productPrompt));

        var response = service.findAll();

        assertThat(response).singleElement()
                .extracting("id", "name", "prompt", "imageUrl")
                .containsExactly(productPrompt.getId(), "Oferta", "Prompt", "https://cdn.example.com/prompt.png");
        verify(productPromptRepository).findAllActiveByTenantId(tenantId);
    }

    @Test
    void getCompanyAssetsShouldReturnCurrentTenantLogo() {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setLogoUrl("https://pub-test.r2.dev/company-logos/logo.png");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        var response = service.getCompanyAssets();

        assertThat(response.getLogoUrl())
                .isEqualTo("https://pub-test.r2.dev/company-logos/logo.png");
        verify(tenantRepository).findById(tenantId);
    }

    @Test
    void createShouldRequireImage() {
        assertThatThrownBy(() -> service.create(request("Oferta", "Prompt"), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Prompt image is required");

        verifyNoInteractions(storageService);
        verify(productPromptRepository, never()).save(any());
    }

    @Test
    void createShouldUploadImageAndPersistPromptForCurrentTenant() {
        MockMultipartFile image = image();
        when(storageService.uploadImage(image)).thenReturn("https://cdn.example.com/prompt.png");
        when(productPromptRepository.save(any(ProductPrompt.class))).thenAnswer(invocation -> {
            ProductPrompt productPrompt = invocation.getArgument(0);
            productPrompt.setId(UUID.randomUUID());
            productPrompt.setCreatedAt(LocalDateTime.now());
            productPrompt.setUpdatedAt(LocalDateTime.now());
            return productPrompt;
        });

        var response = service.create(request("  Oferta  ", "  Prompt base  "), image);

        assertThat(response.getName()).isEqualTo("Oferta");
        assertThat(response.getPrompt()).isEqualTo("Prompt base");
        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.com/prompt.png");
        verify(productPromptRepository).save(any(ProductPrompt.class));
    }

    @Test
    void findAndUpdateShouldRejectPromptFromAnotherTenant() {
        UUID promptId = UUID.randomUUID();
        when(productPromptRepository.findActiveByTenantIdAndId(tenantId, promptId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(promptId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(promptId, request("Nome", "Prompt"), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateShouldKeepImageWhenNewImageIsNotProvided() {
        ProductPrompt productPrompt = productPrompt("Antigo", "Prompt antigo", "https://cdn.example.com/old.png");
        when(productPromptRepository.findActiveByTenantIdAndId(tenantId, productPrompt.getId()))
                .thenReturn(Optional.of(productPrompt));
        when(productPromptRepository.save(productPrompt)).thenReturn(productPrompt);

        var response = service.update(productPrompt.getId(), request("Novo", "Prompt novo"), null);

        assertThat(response.getName()).isEqualTo("Novo");
        assertThat(response.getPrompt()).isEqualTo("Prompt novo");
        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.com/old.png");
        verify(storageService, never()).uploadImage(any());
        verify(storageService, never()).deleteImage(any());
    }

    @Test
    void updateShouldReplaceImageAndDeleteOldImage() {
        ProductPrompt productPrompt = productPrompt("Antigo", "Prompt antigo", "https://cdn.example.com/old.png");
        MockMultipartFile image = image();
        when(productPromptRepository.findActiveByTenantIdAndId(tenantId, productPrompt.getId()))
                .thenReturn(Optional.of(productPrompt));
        when(storageService.uploadImage(image)).thenReturn("https://cdn.example.com/new.png");
        when(productPromptRepository.save(productPrompt)).thenReturn(productPrompt);

        var response = service.update(productPrompt.getId(), request("Novo", "Prompt novo"), image);

        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.com/new.png");
        verify(storageService).deleteImage("https://cdn.example.com/old.png");
    }

    @Test
    void deleteShouldSoftDeleteAndDeleteImage() {
        ProductPrompt productPrompt = productPrompt("Oferta", "Prompt", "https://cdn.example.com/prompt.png");
        when(productPromptRepository.findActiveByTenantIdAndId(tenantId, productPrompt.getId()))
                .thenReturn(Optional.of(productPrompt));
        when(productPromptRepository.save(productPrompt)).thenReturn(productPrompt);

        service.delete(productPrompt.getId());

        assertThat(productPrompt.getDeletedAt()).isNotNull();
        verify(storageService).deleteImage("https://cdn.example.com/prompt.png");
        verify(productPromptRepository).save(productPrompt);
    }

    private ProductPromptRequest request(String name, String prompt) {
        return ProductPromptRequest.builder()
                .name(name)
                .prompt(prompt)
                .build();
    }

    private ProductPrompt productPrompt(String name, String prompt, String imageUrl) {
        ProductPrompt productPrompt = new ProductPrompt();
        productPrompt.setId(UUID.randomUUID());
        productPrompt.setTenantId(tenantId);
        productPrompt.setName(name);
        productPrompt.setPrompt(prompt);
        productPrompt.setImageUrl(imageUrl);
        productPrompt.setCreatedAt(LocalDateTime.now());
        productPrompt.setUpdatedAt(LocalDateTime.now());
        return productPrompt;
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "prompt.png", "image/png", new byte[] { 1 });
    }
}
