package br.com.stockshift.service;

import br.com.stockshift.dto.admin.ProductImageProcessingResult;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.ProductImageThumbnail;
import br.com.stockshift.repository.ProductImageThumbnailRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.imaging.ThumbnailGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductImageProcessingServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductImageThumbnailRepository thumbnailRepository;
    @Mock private StorageService storageService;
    @Mock private ThumbnailGenerator thumbnailGenerator;

    private ProductImageProcessingService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        service = new ProductImageProcessingService(
                productRepository, thumbnailRepository, storageService, thumbnailGenerator);
        lenient().when(storageService.getPublicUrl()).thenReturn("https://cdn.example.com");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldSkipProductWithoutImageUrl() {
        Product product = product(null);
        when(productRepository.findAllByTenantId(tenantId)).thenReturn(List.of(product));

        ProductImageProcessingResult result = service.processAll();

        assertThat(result.total()).isZero();
        assertThat(result.skipped()).isZero();
    }

    @Test
    void shouldSkipProductAlreadyGood() {
        Product product = product("https://cdn.example.com/products/test.png");
        when(productRepository.findAllByTenantId(tenantId)).thenReturn(List.of(product));
        when(storageService.headObject("products/test.png"))
                .thenReturn(new StorageService.HeadObjectResult(50_000L, "image/png"));
        when(thumbnailRepository.findByProductId(product.getId()))
                .thenReturn(thumbEntities());

        ProductImageProcessingResult result = service.processAll();

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.processed()).isZero();
    }

    @Test
    void shouldCompressLargeOriginal() {
        Product product = product("https://cdn.example.com/products/big.png");
        when(productRepository.findAllByTenantId(tenantId)).thenReturn(List.of(product));
        when(storageService.headObject("products/big.png"))
                .thenReturn(new StorageService.HeadObjectResult(800_000L, "image/png"));
        when(thumbnailRepository.findByProductId(product.getId()))
                .thenReturn(List.of());
        // Generate a valid JPEG image large enough to exceed 700KB threshold
        BufferedImage bi = new BufferedImage(3000, 3000, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = bi.createGraphics();
        // Fill with complex pattern to prevent high JPEG compression
        for (int y = 0; y < 3000; y += 10) {
            for (int x = 0; x < 3000; x += 10) {
                g2.setColor(new java.awt.Color(
                        (x * 31 + y * 17) % 256,
                        (x * 43 + y * 53) % 256,
                        (x * 71 + y * 37) % 256));
                g2.fillRect(x, y, 10, 10);
            }
        }
        g2.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(bi, "jpg", baos);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test image", e);
        }
        byte[] largeImage = baos.toByteArray();
        when(storageService.getObject("products/big.png")).thenReturn(largeImage);
        when(thumbnailGenerator.generate(any(), anyString(), anyString(), any()))
                .thenAnswer(inv -> new ThumbnailGenerator.ThumbnailResult(
                        new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}), 150, 150, 3L, "jpg"));

        ProductImageProcessingResult result = service.processAll();

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.compressed()).isEqualTo(1);
    }

    @Test
    void shouldKeepExistingThumbnailsWhenRegenerationFails() {
        // PR #5 review: a product with working thumbnails being reprocessed must not lose them
        // if regeneration fails — old rows/objects are retired only after replacements exist.
        Product product = product("https://cdn.example.com/products/test.png");
        when(productRepository.findAllByTenantId(tenantId)).thenReturn(List.of(product));
        // Original > 700KB so the "already good" skip does not apply despite existing thumbnails.
        when(storageService.headObject("products/test.png"))
                .thenReturn(new StorageService.HeadObjectResult(800_000L, "image/png"));
        ProductImageThumbnail existing = new ProductImageThumbnail();
        existing.setProductId(product.getId());
        existing.setSize("sm");
        existing.setStorageKey("products/test_sm.jpg");
        when(thumbnailRepository.findByProductId(product.getId())).thenReturn(List.of(existing));
        // Small payload so compression is skipped; thumbnailGenerator is unstubbed -> all fail.
        when(storageService.getObject("products/test.png")).thenReturn(new byte[]{1, 2, 3});

        ProductImageProcessingResult result = service.processAll();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.processed()).isZero();
        verify(thumbnailRepository, never()).deleteAll(any());
        verify(storageService, never()).deleteStorageKeyQuietly(any());
    }

    @Test
    void shouldContinueAfterHeadFailure() {
        Product good = product("https://cdn.example.com/products/good.png");
        Product bad = product("https://cdn.example.com/products/missing.png");
        good.setId(UUID.randomUUID());
        bad.setId(UUID.randomUUID());
        when(productRepository.findAllByTenantId(tenantId)).thenReturn(List.of(good, bad));

        when(storageService.headObject("products/good.png"))
                .thenReturn(new StorageService.HeadObjectResult(50_000L, "image/png"));
        when(thumbnailRepository.findByProductId(good.getId()))
                .thenReturn(thumbEntities());

        when(storageService.headObject("products/missing.png"))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

        ProductImageProcessingResult result = service.processAll();

        assertThat(result.total()).isEqualTo(2);
        // Both products are skipped: one via already-good path, one via NoSuchKeyException
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.failed()).isZero();
    }

    @Test
    void processOneShouldReturnErrorForMissingProduct() {
        UUID missingId = UUID.randomUUID();
        when(productRepository.findByTenantIdAndId(tenantId, missingId))
                .thenReturn(Optional.empty());

        ProductImageProcessingResult result = service.processOne(missingId);

        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors().get(0)).contains(missingId.toString());
    }

    private List<ProductImageThumbnail> thumbEntities() {
        ProductImageThumbnail sm = new ProductImageThumbnail();
        sm.setSize("sm");
        ProductImageThumbnail md = new ProductImageThumbnail();
        md.setSize("md");
        ProductImageThumbnail lg = new ProductImageThumbnail();
        lg.setSize("lg");
        return List.of(sm, md, lg);
    }

    private Product product(String imageUrl) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setName("Test Product");
        p.setImageUrl(imageUrl);
        p.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        return p;
    }
}
