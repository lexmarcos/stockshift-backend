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

    @Test
    void shouldDecodeWebPContentType() throws Exception {
        // Decode a real WebP fixture (12x9 RGBA, exercising the alpha/BGRA
        // upsampling path that segfaulted under the native Sejda webp-imageio
        // plugin). The decoder is now the pure-Java TwelveMonkeys imageio-webp:
        // a bad WebP throws IOException instead of crashing the JVM. The plugin
        // is decode-only, so the fixture is loaded from resources rather than
        // produced via ImageIO.write (no WebP writer exists anymore).
        InputStream original = getClass().getResourceAsStream("/images/sample.webp");
        assertThat(original).as("WebP test fixture must be on the classpath").isNotNull();

        ThumbnailGenerator.ThumbnailSpec spec = new ThumbnailGenerator.ThumbnailSpec(150, 0.80f);

        ThumbnailGenerator.ThumbnailResult result = generator.generate(
            original, "image/webp", "photo.webp", spec);

        assertThat(result.formatName()).isEqualTo("jpg");
        assertThat(result.sizeBytes()).isGreaterThan(0);
        assertThat(result.widthPx()).isGreaterThan(0);
    }

    private InputStream createTestImage(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new ByteArrayInputStream(out.toByteArray());
    }
}
