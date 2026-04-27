package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.tenant.CompanyConfigResponse;
import br.com.stockshift.dto.tenant.InfinitePayConfigResponse;
import br.com.stockshift.exception.StorageException;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.StorageService;
import br.com.stockshift.util.SanitizationUtil;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class TenantController {

    private final TenantRepository tenantRepository;
    @Autowired(required = false)
    @Nullable
    private StorageService storageService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CompanyConfigResponse>> getCompanyConfig() {
        UUID tenantId = TenantContext.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        return ResponseEntity.ok(ApiResponse.success("Company config retrieved",
                mapCompanyConfigResponse(tenant)));
    }

    @Transactional
    @PutMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<CompanyConfigResponse>> updateCompanyConfig(
            @RequestBody UpdateCompanyRequest request) {
        return updateCompanyConfig(request, null);
    }

    @Transactional
    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CompanyConfigResponse>> updateCompanyConfigWithLogo(
            @RequestPart("company") UpdateCompanyRequest request,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {
        return updateCompanyConfig(request, logo);
    }

    private ResponseEntity<ApiResponse<CompanyConfigResponse>> updateCompanyConfig(
            UpdateCompanyRequest request,
            MultipartFile logo) {
        UUID tenantId = TenantContext.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        String previousLogoUrl = tenant.getLogoUrl();
        if (request.getBusinessName() != null && !request.getBusinessName().isBlank()) {
            tenant.setBusinessName(request.getBusinessName());
        }
        if (request.getDocument() != null && !request.getDocument().isBlank()) {
            tenant.setDocument(request.getDocument());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            tenant.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            tenant.setPhone(request.getPhone());
        }
        if (logo != null && !logo.isEmpty()) {
            tenant.setLogoUrl(uploadCompanyLogo(logo));
        }
        tenantRepository.save(tenant);
        deleteReplacedLogo(previousLogoUrl, tenant.getLogoUrl());

        return ResponseEntity.ok(ApiResponse.success("Company config updated",
                mapCompanyConfigResponse(tenant)));
    }

    private String uploadCompanyLogo(MultipartFile logo) {
        if (storageService == null) {
            throw new StorageException("Storage is not configured for company logo uploads");
        }
        return SanitizationUtil.sanitizeUrl(storageService.uploadCompanyLogo(logo));
    }

    private void deleteReplacedLogo(String previousLogoUrl, String currentLogoUrl) {
        if (storageService == null || previousLogoUrl == null || previousLogoUrl.equals(currentLogoUrl)) {
            return;
        }
        storageService.deleteImage(previousLogoUrl);
    }

    private CompanyConfigResponse mapCompanyConfigResponse(Tenant tenant) {
        return CompanyConfigResponse.builder()
                .businessName(tenant.getBusinessName())
                .document(tenant.getDocument())
                .email(tenant.getEmail())
                .phone(tenant.getPhone())
                .logoUrl(tenant.getLogoUrl())
                .isActive(tenant.getIsActive())
                .build();
    }

    @GetMapping("/me/infinitepay")
    public ResponseEntity<ApiResponse<InfinitePayConfigResponse>> getInfinitePayConfig() {
        UUID tenantId = TenantContext.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        boolean configured = tenant.getInfinitepayHandle() != null
                && !tenant.getInfinitepayHandle().isBlank()
                && tenant.getInfinitepayDocNumber() != null
                && !tenant.getInfinitepayDocNumber().isBlank();

        return ResponseEntity.ok(ApiResponse.success("InfinitePay config retrieved",
                InfinitePayConfigResponse.builder()
                        .handle(tenant.getInfinitepayHandle())
                        .docNumber(tenant.getInfinitepayDocNumber())
                        .configured(configured)
                        .build()));
    }

    @PutMapping("/me/infinitepay")
    public ResponseEntity<ApiResponse<InfinitePayConfigResponse>> updateInfinitePayConfig(
            @RequestBody UpdateInfinitePayRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        tenant.setInfinitepayHandle(request.getHandle());
        tenant.setInfinitepayDocNumber(request.getDocNumber());
        tenantRepository.save(tenant);

        boolean configured = request.getHandle() != null && !request.getHandle().isBlank()
                && request.getDocNumber() != null && !request.getDocNumber().isBlank();

        return ResponseEntity.ok(ApiResponse.success("InfinitePay config updated",
                InfinitePayConfigResponse.builder()
                        .handle(request.getHandle())
                        .docNumber(request.getDocNumber())
                        .configured(configured)
                        .build()));
    }

    @Data
    public static class UpdateCompanyRequest {
        @NotBlank(message = "Business name is required")
        private String businessName;
        private String document;
        @Email(message = "Email must be valid")
        private String email;
        private String phone;
    }

    @Data
    public static class UpdateInfinitePayRequest {
        private String handle;
        @NotBlank(message = "Document number is required")
        private String docNumber;
    }
}
