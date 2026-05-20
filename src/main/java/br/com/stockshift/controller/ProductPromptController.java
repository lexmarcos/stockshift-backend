package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.productprompt.ProductPromptCompanyAssetsResponse;
import br.com.stockshift.dto.productprompt.ProductPromptRequest;
import br.com.stockshift.dto.productprompt.ProductPromptResponse;
import br.com.stockshift.service.ProductPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/product-prompts")
@RequiredArgsConstructor
@Tag(name = "Product Prompts", description = "Tenant product prompt library endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductPromptController {

    private final ProductPromptService productPromptService;

    @GetMapping
    @PreAuthorize("@permissionGuard.hasAny('product_prompts:read')")
    @Operation(summary = "Get all product prompts for current tenant")
    public ResponseEntity<ApiResponse<List<ProductPromptResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(productPromptService.findAll()));
    }

    @GetMapping("/company-assets")
    @PreAuthorize("@permissionGuard.hasAny('product_prompts:read')")
    @Operation(summary = "Get company assets for product prompt generation")
    public ResponseEntity<ApiResponse<ProductPromptCompanyAssetsResponse>> getCompanyAssets() {
        return ResponseEntity.ok(ApiResponse.success(productPromptService.getCompanyAssets()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.hasAny('product_prompts:create')")
    @Operation(summary = "Create a product prompt")
    public ResponseEntity<ApiResponse<ProductPromptResponse>> create(
            @RequestPart("prompt") @Valid ProductPromptRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        ProductPromptResponse response = productPromptService.create(request, image);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product prompt created successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionGuard.hasAny('product_prompts:read')")
    @Operation(summary = "Get product prompt by ID")
    public ResponseEntity<ApiResponse<ProductPromptResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productPromptService.findById(id)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.hasAny('product_prompts:update')")
    @Operation(summary = "Update a product prompt")
    public ResponseEntity<ApiResponse<ProductPromptResponse>> update(
            @PathVariable UUID id,
            @RequestPart("prompt") @Valid ProductPromptRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        ProductPromptResponse response = productPromptService.update(id, request, image);
        return ResponseEntity.ok(ApiResponse.success("Product prompt updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionGuard.hasAny('product_prompts:delete')")
    @Operation(summary = "Delete a product prompt (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        productPromptService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Product prompt deleted successfully", null));
    }
}
