package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.brand.BrandRequest;
import br.com.stockshift.dto.brand.BrandResponse;
import br.com.stockshift.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
@Tag(name = "Brands", description = "Brand management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class BrandController {

    private final BrandService brandService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('BRAND_CREATE', 'ROLE_ADMIN')")
    @Operation(summary = "Create a new brand")
    public ResponseEntity<ApiResponse<BrandResponse>> create(@Valid @RequestBody BrandRequest request) {
        BrandResponse response = brandService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Brand created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('BRAND_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get all brands")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> findAll() {
        List<BrandResponse> brands = brandService.findAll();
        return ResponseEntity.ok(ApiResponse.success(brands));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('BRAND_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get brand by ID")
    public ResponseEntity<ApiResponse<BrandResponse>> findById(@PathVariable Long id) {
        BrandResponse response = brandService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('BRAND_UPDATE', 'ROLE_ADMIN')")
    @Operation(summary = "Update brand")
    public ResponseEntity<ApiResponse<BrandResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody BrandRequest request) {
        BrandResponse response = brandService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Brand updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('BRAND_DELETE', 'ROLE_ADMIN')")
    @Operation(summary = "Delete brand (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        brandService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Brand deleted successfully", null));
    }
}
