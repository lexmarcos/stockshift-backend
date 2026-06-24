package br.com.stockshift.service;

import br.com.stockshift.config.StorageProperties;
import br.com.stockshift.exception.InvalidFileTypeException;
import br.com.stockshift.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private StorageProperties properties;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(s3Client, properties);
    }

    @Test
    void shouldRejectInvalidFileType() {
        MultipartFile file = new MockMultipartFile(
            "file",
            "test.pdf",
            "application/pdf",
            "test data".getBytes()
        );

        assertThrows(InvalidFileTypeException.class, () -> {
            storageService.uploadImage(file);
        });
    }

    @Test
    void shouldAcceptValidImageTypes() {
        String[] validTypes = {"image/png", "image/jpeg", "image/jpg", "image/webp"};

        for (String type : validTypes) {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                type,
                "test data".getBytes()
            );

            // Should not throw exception during validation
            assertDoesNotThrow(() -> {
                try {
                    storageService.uploadImage(file);
                } catch (StorageException e) {
                    // Ignore storage exceptions, we're only testing validation
                }
            });
        }
    }

    @Test
    void shouldAcceptValidCompanyLogoTypes() {
        String[] validTypes = {"image/svg+xml", "image/png", "image/jpeg", "image/jpg"};

        for (String type : validTypes) {
            MockMultipartFile file = new MockMultipartFile(
                "logo",
                "logo.svg",
                type,
                "test data".getBytes()
            );

            assertDoesNotThrow(() -> {
                try {
                    storageService.uploadCompanyLogo(file);
                } catch (StorageException e) {
                    // Ignore storage exceptions, we're only testing validation
                }
            });
        }
    }

    @Test
    void shouldRejectCompanyLogoWebp() {
        MultipartFile file = new MockMultipartFile(
            "logo",
            "logo.webp",
            "image/webp",
            "test data".getBytes()
        );

        assertThrows(InvalidFileTypeException.class, () -> {
            storageService.uploadCompanyLogo(file);
        });
    }

    @Test
    void shouldRejectCompanyLogoLargerThanTwoMb() {
        byte[] content = new byte[(2 * 1024 * 1024) + 1];
        MultipartFile file = new MockMultipartFile(
            "logo",
            "logo.png",
            "image/png",
            content
        );

        assertThrows(InvalidFileTypeException.class, () -> {
            storageService.uploadCompanyLogo(file);
        });
    }

    @Test
    void deleteProductImagesShouldHandleNullThumbnailKeys() {
        when(properties.getPublicUrl()).thenReturn("https://cdn.example.com");

        assertDoesNotThrow(() -> storageService.deleteProductImages(
            "https://cdn.example.com/products/test.png", null));
    }
}
