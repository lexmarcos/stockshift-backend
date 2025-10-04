package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.variant.CreateProductVariantRequest;
import com.stockshift.backend.api.dto.variant.ProductVariantResponse;
import com.stockshift.backend.api.dto.variant.UpdateProductVariantRequest;
import com.stockshift.backend.api.mapper.ProductVariantMapper;
import com.stockshift.backend.application.service.ProductVariantService;
import com.stockshift.backend.domain.product.ProductVariant;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductVariantController {

    private final ProductVariantService variantService;
    private final ProductVariantMapper variantMapper;

    @PostMapping("/products/{productId}/variants")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ProductVariantResponse> createVariant(
            @PathVariable(value = "productId") UUID productId,
            @Valid @RequestBody CreateProductVariantRequest request
    ) {
        ProductVariant variant = variantService.createVariant(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(variantMapper.toResponse(variant));
    }

    @GetMapping("/products/{productId}/variants")
    public ResponseEntity<Page<ProductVariantResponse>> getVariantsByProduct(
            @PathVariable(value = "productId") UUID productId,
            @RequestParam(value = "onlyActive", required = false, defaultValue = "false") Boolean onlyActive,
            @PageableDefault(size = 20, sort = "sku", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<ProductVariant> variants = variantService.getVariantsByProduct(productId, pageable);
        return ResponseEntity.ok(variants.map(variantMapper::toResponse));
    }

    @GetMapping("/variants")
    public ResponseEntity<Page<ProductVariantResponse>> getAllVariants(
            @RequestParam(value = "onlyActive", required = false, defaultValue = "false") Boolean onlyActive,
            @PageableDefault(size = 20, sort = "sku", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<ProductVariant> variants = onlyActive 
            ? variantService.getActiveVariants(pageable)
            : variantService.getAllVariants(pageable);
        return ResponseEntity.ok(variants.map(variantMapper::toResponse));
    }

    @GetMapping("/variants/{id}")
    public ResponseEntity<ProductVariantResponse> getVariantById(
            @PathVariable(value = "id") UUID id
    ) {
        ProductVariant variant = variantService.getVariantById(id);
        return ResponseEntity.ok(variantMapper.toResponse(variant));
    }

    @GetMapping("/variants/sku/{sku}")
    public ResponseEntity<ProductVariantResponse> getVariantBySku(
            @PathVariable(value = "sku") String sku
    ) {
        ProductVariant variant = variantService.getVariantBySku(sku)
            .orElseThrow(() -> new RuntimeException("Variant not found with SKU: " + sku));
        return ResponseEntity.ok(variantMapper.toResponse(variant));
    }

    @GetMapping("/variants/gtin/{gtin}")
    public ResponseEntity<ProductVariantResponse> getVariantByGtin(
            @PathVariable(value = "gtin") String gtin
    ) {
        ProductVariant variant = variantService.getVariantByGtin(gtin)
            .orElseThrow(() -> new RuntimeException("Variant not found with GTIN: " + gtin));
        return ResponseEntity.ok(variantMapper.toResponse(variant));
    }

    @PutMapping("/variants/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ProductVariantResponse> updateVariant(
            @PathVariable(value = "id") UUID id,
            @Valid @RequestBody UpdateProductVariantRequest request
    ) {
        ProductVariant variant = variantService.updateVariant(id, request);
        return ResponseEntity.ok(variantMapper.toResponse(variant));
    }

    @DeleteMapping("/variants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteVariant(@PathVariable(value = "id") UUID id) {
        variantService.deleteVariant(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/variants/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductVariantResponse> activateVariant(@PathVariable(value = "id") UUID id) {
        variantService.activateVariant(id);
        ProductVariant variant = variantService.getVariantById(id);
        return ResponseEntity.ok(variantMapper.toResponse(variant));
    }
}
