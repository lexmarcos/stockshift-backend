package br.com.stockshift.service.imaging;

import java.io.InputStream;

public interface ThumbnailGenerator {

    record ThumbnailResult(
            InputStream inputStream,
            int widthPx,
            int heightPx,
            long sizeBytes,
            String formatName
    ) {}

    record ThumbnailSpec(int maxWidth, float quality) {}

    ThumbnailResult generate(InputStream original, String contentType, String fileName, ThumbnailSpec spec);
}
