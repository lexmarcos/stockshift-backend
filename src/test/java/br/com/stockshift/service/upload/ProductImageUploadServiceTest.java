package br.com.stockshift.service.upload;

import br.com.stockshift.dto.upload.TemporaryProductImageUploadResponse;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.model.entity.ProductImageUpload;
import br.com.stockshift.model.enums.ProductImageUploadStatus;
import br.com.stockshift.repository.ProductImageUploadRepository;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.StorageService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductImageUploadServiceTest {

    @Mock
    private ProductImageUploadRepository uploadRepository;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private StorageService storageService;

    private ProductImageUploadService service;
    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        service = new ProductImageUploadService(uploadRepository, securityUtils);
        ReflectionTestUtils.setField(service, "storageService", storageService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldCreatePendingTemporaryUpload() {
        MockMultipartFile image = new MockMultipartFile("image", "p.png", "image/png", new byte[]{1});
        UUID generatedUploadId = UUID.randomUUID();
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(storageService.uploadTemporaryProductImage(eq(image), eq(tenantId), any()))
                .thenReturn(new StorageService.StoredImageObject("temp/key.png", "https://cdn/temp/key.png"));
        when(uploadRepository.save(any(ProductImageUpload.class))).thenAnswer(invocation -> {
            ProductImageUpload upload = invocation.getArgument(0);
            assertThat(upload.getId()).isNull();
            upload.setId(generatedUploadId);
            return upload;
        });

        TemporaryProductImageUploadResponse response = service.uploadTemporaryProductImage(image);

        assertThat(response.getUploadId()).isEqualTo(generatedUploadId);
        assertThat(response.getFileName()).isEqualTo("p.png");
        verify(uploadRepository).save(any(ProductImageUpload.class));
    }

    @Test
    void shouldPromotePendingUploadForCurrentUser() {
        UUID uploadId = UUID.randomUUID();
        ProductImageUpload upload = pendingUpload(uploadId);
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(uploadRepository.findForCurrentUser(tenantId, uploadId, userId)).thenReturn(Optional.of(upload));
        when(storageService.copyTemporaryProductImageToProductImage("temp/key.png", "p.png"))
                .thenReturn(new StorageService.StoredImageObject("products/key.png", "https://cdn/products/key.png"));

        ProductImageUploadClaim claim = service.promotePendingUpload(uploadId);

        assertThat(claim.uploadId()).isEqualTo(uploadId);
        assertThat(claim.finalImageUrl()).isEqualTo("https://cdn/products/key.png");
    }

    @Test
    void shouldRejectInvalidPendingUpload() {
        UUID uploadId = UUID.randomUUID();
        ProductImageUpload upload = pendingUpload(uploadId);
        upload.setStatus(ProductImageUploadStatus.CONSUMED);
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(uploadRepository.findForCurrentUser(tenantId, uploadId, userId)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.promotePendingUpload(uploadId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not pending");
    }

    @Test
    void shouldExpirePendingUploads() {
        ProductImageUpload upload = pendingUpload(UUID.randomUUID());
        upload.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(uploadRepository.findByStatusAndExpiresAtBefore(eq(ProductImageUploadStatus.PENDING), any()))
                .thenReturn(List.of(upload));

        int expiredCount = service.cleanupExpiredUploads();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(upload.getStatus()).isEqualTo(ProductImageUploadStatus.EXPIRED);
        verify(storageService).deleteStorageKeyQuietly("temp/key.png");
        verify(uploadRepository).saveAll(List.of(upload));
    }

    private ProductImageUpload pendingUpload(UUID uploadId) {
        ProductImageUpload upload = new ProductImageUpload();
        upload.setId(uploadId);
        upload.setTenantId(tenantId);
        upload.setUploadedByUserId(userId);
        upload.setStorageKey("temp/key.png");
        upload.setPublicUrl("https://cdn/temp/key.png");
        upload.setOriginalName("p.png");
        upload.setContentType("image/png");
        upload.setSizeBytes(1L);
        upload.setStatus(ProductImageUploadStatus.PENDING);
        upload.setExpiresAt(LocalDateTime.now().plusHours(1));
        return upload;
    }
}
