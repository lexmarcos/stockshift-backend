package br.com.stockshift.service.upload;

import java.util.UUID;

public record ProductImageUploadClaim(
        UUID uploadId,
        String temporaryStorageKey,
        String finalStorageKey,
        String finalImageUrl) {
}
