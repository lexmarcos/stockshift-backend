package br.com.stockshift.service.imaging;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ThumbnailatorGeneratorTest {

    private final ThumbnailatorGenerator generator = new ThumbnailatorGenerator();

    @Test
    void shouldResizeImageToTargetWidth() throws Exception {
        InputStream original = createTestImage(600, 400);
        ThumbnailGenerator.ThumbnailSpec spec = new ThumbnailGenerator.ThumbnailSpec(150, 0.80f);

        ThumbnailGenerator.ThumbnailResult result = generator.generate(
            original, "image/png", "test.png", spec);

        assertThat(result.widthPx()).isEqualTo(150);
        assertThat(result.heightPx()).isEqualTo(100); // 400 * 150/600
        assertThat(result.formatName()).isEqualTo("jpg");
        assertThat(result.sizeBytes()).isGreaterThan(0);
    }

    @Test
    void shouldNotUpscaleWhenImageIsSmallerThanTarget() throws Exception {
        InputStream original = createTestImage(100, 100);
        ThumbnailGenerator.ThumbnailSpec spec = new ThumbnailGenerator.ThumbnailSpec(800, 0.85f);

        ThumbnailGenerator.ThumbnailResult result = generator.generate(
            original, "image/png", "small.png", spec);

        assertThat(result.widthPx()).isEqualTo(100); // no upscale
    }

    private InputStream createTestImage(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new ByteArrayInputStream(out.toByteArray());
    }
}
