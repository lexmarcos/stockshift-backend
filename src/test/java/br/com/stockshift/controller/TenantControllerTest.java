package br.com.stockshift.controller;

import br.com.stockshift.exception.StorageException;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantControllerTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private StorageService storageService;

    private TenantController controller;
    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        tenant = tenant();
        controller = new TenantController(tenantRepository);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void companyConfigShouldReadAndUpdateJsonFields() {
        TenantController.UpdateCompanyRequest request = new TenantController.UpdateCompanyRequest();
        request.setBusinessName("Nova Empresa");
        request.setDocument("999");
        request.setEmail("novo@example.com");
        request.setPhone("(11) 99999-9999");

        var before = controller.getCompanyConfig().getBody().getData();
        var after = controller.updateCompanyConfig(request).getBody().getData();

        assertThat(before.getBusinessName()).isEqualTo("Empresa");
        assertThat(after.getBusinessName()).isEqualTo("Nova Empresa");
        assertThat(after.getDocument()).isEqualTo("999");
        assertThat(after.getEmail()).isEqualTo("novo@example.com");
        assertThat(after.getPhone()).isEqualTo("(11) 99999-9999");
        verify(tenantRepository).save(tenant);
    }

    @Test
    void logoUpdateShouldUploadNewLogoAndDeleteReplacedLogo() {
        ReflectionTestUtils.setField(controller, "storageService", storageService);
        TenantController.UpdateCompanyRequest request = new TenantController.UpdateCompanyRequest();
        request.setBusinessName("Empresa com logo");
        request.setDocument("123");
        request.setEmail("empresa@example.com");
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[]{1});
        when(storageService.uploadCompanyLogo(logo)).thenReturn("https://cdn.example.com/new-logo.png");

        var response = controller.updateCompanyConfigWithLogo(request, logo).getBody().getData();

        assertThat(response.getLogoUrl()).isEqualTo("https://cdn.example.com/new-logo.png");
        verify(storageService).deleteImage("https://cdn.example.com/old-logo.png");
    }

    @Test
    void logoUpdateShouldFailWhenStorageIsNotConfigured() {
        TenantController.UpdateCompanyRequest request = new TenantController.UpdateCompanyRequest();
        request.setBusinessName("Empresa");
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> controller.updateCompanyConfigWithLogo(request, logo))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Storage is not configured");
    }

    @Test
    void infinitePayConfigShouldExposeConfiguredFlagAndPersistUpdates() {
        assertThat(controller.getInfinitePayConfig().getBody().getData().isConfigured()).isTrue();

        TenantController.UpdateInfinitePayRequest request = new TenantController.UpdateInfinitePayRequest();
        request.setHandle("");
        request.setDocNumber(" ");

        var response = controller.updateInfinitePayConfig(request).getBody().getData();

        assertThat(response.isConfigured()).isFalse();
        assertThat(tenant.getInfinitepayHandle()).isEmpty();
        assertThat(tenant.getInfinitepayDocNumber()).isBlank();
        verify(tenantRepository).save(tenant);
    }

    private Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setBusinessName("Empresa");
        tenant.setDocument("123");
        tenant.setEmail("empresa@example.com");
        tenant.setPhone("(81) 3000-0000");
        tenant.setLogoUrl("https://cdn.example.com/old-logo.png");
        tenant.setIsActive(true);
        tenant.setInfinitepayHandle("empresa");
        tenant.setInfinitepayDocNumber("12345678900");
        return tenant;
    }
}
