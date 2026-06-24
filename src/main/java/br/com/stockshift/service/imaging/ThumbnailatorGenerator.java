package br.com.stockshift.service.imaging;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
public class ThumbnailatorGenerator implements ThumbnailGenerator {

    private static final String OUTPUT_FORMAT = "jpg";

    @Override
    public ThumbnailResult generate(InputStream original, String contentType, String fileName, ThumbnailSpec spec) {
        try {
            BufferedImage image = ImageIO.read(original);
            if (image == null) {
                throw new IOException("Failed to decode image: unsupported format for " + fileName);
            }

            int targetWidth = Math.min(spec.maxWidth(), image.getWidth());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(image)
                    .width(targetWidth)
                    .outputFormat(OUTPUT_FORMAT)
                    .outputQuality(spec.quality())
                    .toOutputStream(out);

            byte[] bytes = out.toByteArray();
            int outHeight = Math.round((float) targetWidth / image.getWidth() * image.getHeight());

            return new ThumbnailResult(
                    new ByteArrayInputStream(bytes),
                    targetWidth,
                    outHeight,
                    bytes.length,
                    OUTPUT_FORMAT
            );
        } catch (IOException e) {
            log.warn("Failed to generate thumbnail for {}: {}", fileName, e.getMessage());
            throw new RuntimeException("Thumbnail generation failed: " + fileName, e);
        }
    }
}
