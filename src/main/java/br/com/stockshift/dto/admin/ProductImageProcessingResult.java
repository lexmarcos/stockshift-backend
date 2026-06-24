package br.com.stockshift.dto.admin;

import java.util.List;

public record ProductImageProcessingResult(
    int total,
    int processed,
    int skipped,
    int compressed,
    int failed,
    List<String> errors
) {
    public static ProductImageProcessingResult empty() {
        return new ProductImageProcessingResult(0, 0, 0, 0, 0, List.of());
    }
}
