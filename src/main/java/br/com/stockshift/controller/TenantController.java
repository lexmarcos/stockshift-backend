package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.tenant.InfinitePayConfigResponse;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.security.TenantContext;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    public static class UpdateInfinitePayRequest {
        @NotBlank(message = "Handle is required")
        private String handle;
        @NotBlank(message = "Document number is required")
        private String docNumber;
    }
}
