package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.tenant.CompanyConfigResponse;
import br.com.stockshift.dto.tenant.InfinitePayConfigResponse;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.security.TenantContext;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class TenantController {

    private final TenantRepository tenantRepository;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CompanyConfigResponse>> getCompanyConfig() {
        UUID tenantId = TenantContext.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        return ResponseEntity.ok(ApiResponse.success("Company config retrieved",
                CompanyConfigResponse.builder()
                        .businessName(tenant.getBusinessName())
                        .document(tenant.getDocument())
                        .email(tenant.getEmail())
                        .phone(tenant.getPhone())
                        .isActive(tenant.getIsActive())
                        .build()));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<CompanyConfigResponse>> updateCompanyConfig(
            @RequestBody UpdateCompanyRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

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
        tenantRepository.save(tenant);

        return ResponseEntity.ok(ApiResponse.success("Company config updated",
                CompanyConfigResponse.builder()
                        .businessName(tenant.getBusinessName())
                        .document(tenant.getDocument())
                        .email(tenant.getEmail())
                        .phone(tenant.getPhone())
                        .isActive(tenant.getIsActive())
                        .build()));
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
