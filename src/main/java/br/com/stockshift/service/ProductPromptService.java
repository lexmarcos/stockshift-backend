package br.com.stockshift.service;

import br.com.stockshift.dto.productprompt.ProductPromptRequest;
import br.com.stockshift.dto.productprompt.ProductPromptCompanyAssetsResponse;
import br.com.stockshift.dto.productprompt.ProductPromptResponse;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.exception.StorageException;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.ProductPrompt;
import br.com.stockshift.repository.ProductPromptRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.util.SanitizationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductPromptService {

    private final ProductPromptRepository productPromptRepository;
    private final TenantRepository tenantRepository;

    @Autowired(required = false)
    @Nullable
    private StorageService storageService;

    @Transactional(readOnly = true)
    public List<ProductPromptResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return productPromptRepository.findAllActiveByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductPromptResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        return mapToResponse(findPromptByTenant(id, tenantId));
    }

    @Transactional(readOnly = true)
    public ProductPromptCompanyAssetsResponse getCompanyAssets() {
        Tenant tenant = tenantRepository.findById(TenantContext.getTenantId()).orElseThrow();
        return ProductPromptCompanyAssetsResponse.builder()
                .logoUrl(SanitizationUtil.sanitizeUrl(tenant.getLogoUrl()))
                .build();
    }

    @Transactional
    public ProductPromptResponse create(ProductPromptRequest request, MultipartFile image) {
        UUID tenantId = TenantContext.getTenantId();
        validateRequiredImage(image);

        String uploadedImageUrl = uploadAndSanitizeImage(image);
        try {
            ProductPrompt productPrompt = new ProductPrompt();
            productPrompt.setTenantId(tenantId);
            productPrompt.setName(sanitizeText(request.getName()));
            productPrompt.setPrompt(sanitizeText(request.getPrompt()));
            productPrompt.setImageUrl(uploadedImageUrl);

            ProductPrompt saved = productPromptRepository.save(productPrompt);
            log.info("Created product prompt {} for tenant {}", saved.getId(), tenantId);
            return mapToResponse(saved);
        } catch (RuntimeException exception) {
            deleteImageQuietly(uploadedImageUrl);
            throw exception;
        }
    }

    @Transactional
    public ProductPromptResponse update(UUID id, ProductPromptRequest request, MultipartFile image) {
        UUID tenantId = TenantContext.getTenantId();
        ProductPrompt productPrompt = findPromptByTenant(id, tenantId);
        String oldImageUrl = productPrompt.getImageUrl();
        String newImageUrl = hasImage(image) ? uploadAndSanitizeImage(image) : null;

        try {
            productPrompt.setName(sanitizeText(request.getName()));
            productPrompt.setPrompt(sanitizeText(request.getPrompt()));
            if (newImageUrl != null) {
                productPrompt.setImageUrl(newImageUrl);
            }

            ProductPrompt updated = productPromptRepository.save(productPrompt);
            if (newImageUrl != null) {
                deleteImageQuietly(oldImageUrl);
            }
            log.info("Updated product prompt {} for tenant {}", id, tenantId);
            return mapToResponse(updated);
        } catch (RuntimeException exception) {
            if (newImageUrl != null) {
                deleteImageQuietly(newImageUrl);
            }
            throw exception;
        }
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        ProductPrompt productPrompt = findPromptByTenant(id, tenantId);

        productPrompt.setDeletedAt(LocalDateTime.now());
        productPromptRepository.save(productPrompt);
        deleteImageQuietly(productPrompt.getImageUrl());

        log.info("Soft deleted product prompt {} for tenant {}", id, tenantId);
    }

    private ProductPrompt findPromptByTenant(UUID id, UUID tenantId) {
        return productPromptRepository.findActiveByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductPrompt", "id", id));
    }

    private void validateRequiredImage(MultipartFile image) {
        if (!hasImage(image)) {
            throw new BadRequestException("Prompt image is required");
        }
    }

    private boolean hasImage(MultipartFile image) {
        return image != null && !image.isEmpty();
    }

    private String uploadAndSanitizeImage(MultipartFile image) {
        StorageService storage = requireStorage();
        String imageUrl = storage.uploadImage(image);
        String sanitizedImageUrl = SanitizationUtil.sanitizeUrl(imageUrl);
        if (sanitizedImageUrl == null) {
            deleteImageQuietly(imageUrl);
            throw new StorageException("Uploaded image URL is invalid");
        }
        return sanitizedImageUrl;
    }

    private StorageService requireStorage() {
        if (storageService == null) {
            throw new StorageException("Image storage is not configured");
        }
        return storageService;
    }

    private String sanitizeText(String value) {
        return SanitizationUtil.sanitizeForHtml(value == null ? null : value.trim());
    }

    private void deleteImageQuietly(String imageUrl) {
        if (imageUrl == null || storageService == null) {
            return;
        }

        try {
            storageService.deleteImage(imageUrl);
        } catch (RuntimeException exception) {
            log.warn("Failed to delete product prompt image: {}", imageUrl, exception);
        }
    }

    private ProductPromptResponse mapToResponse(ProductPrompt productPrompt) {
        return ProductPromptResponse.builder()
                .id(productPrompt.getId())
                .name(productPrompt.getName())
                .prompt(productPrompt.getPrompt())
                .imageUrl(SanitizationUtil.sanitizeUrl(productPrompt.getImageUrl()))
                .createdAt(productPrompt.getCreatedAt())
                .updatedAt(productPrompt.getUpdatedAt())
                .build();
    }
}
