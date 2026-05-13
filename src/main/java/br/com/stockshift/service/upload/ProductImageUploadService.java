package br.com.stockshift.service.upload;

import br.com.stockshift.dto.upload.TemporaryProductImageUploadResponse;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.StorageException;
import br.com.stockshift.model.entity.ProductImageUpload;
import br.com.stockshift.model.enums.ProductImageUploadStatus;
import br.com.stockshift.repository.ProductImageUploadRepository;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImageUploadService {

    private static final int TEMP_UPLOAD_TTL_HOURS = 24;

    private final ProductImageUploadRepository uploadRepository;
    private final SecurityUtils securityUtils;

    @Autowired(required = false)
    @Nullable
    private StorageService storageService;

    @Transactional
    public TemporaryProductImageUploadResponse uploadTemporaryProductImage(MultipartFile image) {
        StorageService storage = requireStorage();
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = securityUtils.getCurrentUserId();
        UUID storageObjectId = UUID.randomUUID();
        StorageService.StoredImageObject stored = storage.uploadTemporaryProductImage(image, tenantId, storageObjectId);
        ProductImageUpload upload = savePendingUpload(image, tenantId, userId, stored);
        return toResponse(upload);
    }

    @Transactional
    public ProductImageUploadClaim promotePendingUpload(UUID uploadId) {
        ProductImageUpload upload = findCurrentUserUpload(uploadId);
        validatePendingUpload(upload);
        StorageService.StoredImageObject promoted = requireStorage()
                .copyTemporaryProductImageToProductImage(upload.getStorageKey(), upload.getOriginalName());
        return new ProductImageUploadClaim(
                upload.getId(),
                upload.getStorageKey(),
                promoted.key(),
                promoted.publicUrl());
    }

    @Transactional
    public void markConsumed(ProductImageUploadClaim claim) {
        ProductImageUpload upload = findCurrentUserUpload(claim.uploadId());
        upload.setStatus(ProductImageUploadStatus.CONSUMED);
        upload.setConsumedAt(LocalDateTime.now());
        uploadRepository.save(upload);
    }

    public void registerStorageCleanup(List<ProductImageUploadClaim> claims) {
        if (claims.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteTemporaryImages(claims);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(buildCleanupSynchronization(claims));
    }

    @Transactional
    public int cleanupExpiredUploads() {
        List<ProductImageUpload> uploads = uploadRepository.findByStatusAndExpiresAtBefore(
                ProductImageUploadStatus.PENDING,
                LocalDateTime.now());
        expireUploads(uploads);
        return uploads.size();
    }

    public void deleteFinalImagesQuietly(List<ProductImageUploadClaim> claims) {
        StorageService storage = storageService;
        if (storage == null) {
            return;
        }
        claims.forEach(claim -> storage.deleteStorageKeyQuietly(claim.finalStorageKey()));
    }

    private ProductImageUpload savePendingUpload(
            MultipartFile image,
            UUID tenantId,
            UUID userId,
            StorageService.StoredImageObject stored) {
        ProductImageUpload upload = new ProductImageUpload();
        upload.setTenantId(tenantId);
        upload.setUploadedByUserId(userId);
        upload.setStorageKey(stored.key());
        upload.setPublicUrl(stored.publicUrl());
        upload.setOriginalName(image.getOriginalFilename());
        upload.setContentType(image.getContentType());
        upload.setSizeBytes(image.getSize());
        upload.setStatus(ProductImageUploadStatus.PENDING);
        upload.setExpiresAt(LocalDateTime.now().plusHours(TEMP_UPLOAD_TTL_HOURS));
        return uploadRepository.save(upload);
    }

    private ProductImageUpload findCurrentUserUpload(UUID uploadId) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = securityUtils.getCurrentUserId();
        return uploadRepository.findForCurrentUser(tenantId, uploadId, userId)
                .orElseThrow(() -> new BadRequestException(
                        "Temporary product image upload not found for id: " + uploadId));
    }

    private void validatePendingUpload(ProductImageUpload upload) {
        if (upload.getStatus() != ProductImageUploadStatus.PENDING) {
            throw new BadRequestException("Temporary product image upload is not pending: " + upload.getId());
        }
        if (LocalDateTime.now().isAfter(upload.getExpiresAt())) {
            throw new BadRequestException("Temporary product image upload has expired: " + upload.getId());
        }
    }

    private TransactionSynchronization buildCleanupSynchronization(List<ProductImageUploadClaim> claims) {
        return new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteTemporaryImages(claims);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteFinalImagesQuietly(claims);
                }
            }
        };
    }

    private void expireUploads(List<ProductImageUpload> uploads) {
        StorageService storage = storageService;
        for (ProductImageUpload upload : uploads) {
            if (storage != null) {
                storage.deleteStorageKeyQuietly(upload.getStorageKey());
            }
            upload.setStatus(ProductImageUploadStatus.EXPIRED);
        }
        uploadRepository.saveAll(uploads);
    }

    private void deleteTemporaryImages(List<ProductImageUploadClaim> claims) {
        StorageService storage = storageService;
        if (storage == null) {
            return;
        }
        claims.forEach(claim -> storage.deleteStorageKeyQuietly(claim.temporaryStorageKey()));
    }

    private TemporaryProductImageUploadResponse toResponse(ProductImageUpload upload) {
        return TemporaryProductImageUploadResponse.builder()
                .uploadId(upload.getId())
                .fileName(upload.getOriginalName())
                .contentType(upload.getContentType())
                .sizeBytes(upload.getSizeBytes())
                .build();
    }

    private StorageService requireStorage() {
        if (storageService == null) {
            throw new StorageException("Image upload service is unavailable");
        }
        return storageService;
    }
}
