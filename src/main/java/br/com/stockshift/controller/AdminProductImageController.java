package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.admin.ProductImageProcessingResult;
import br.com.stockshift.service.ProductImageProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.lang.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products")
@ConditionalOnBean(ProductImageProcessingService.class)
public class AdminProductImageController {

    @Autowired(required = false)
    @Nullable
    private ProductImageProcessingService processingService;

    /**
     * Reprocesses product image originals (compress + (re)generate thumbnails).
     *
     * @param productId process only this product; omit to process the whole tenant catalog.
     * @param limit     when processing the catalog, cap on products that actually do work
     *                  (already-good products are skipped for free and do not count). Lets the
     *                  caller drain a large catalog in bounded batches — e.g.
     *                  {@code POST /api/admin/products/process-images?limit=20} — instead of one
     *                  catalog-wide scan. Ignored when {@code productId} is given.
     */
    @PostMapping("/process-images")
    @PreAuthorize("@permissionGuard.has('products:update')")
    public ResponseEntity<ApiResponse<ProductImageProcessingResult>> processImages(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) Integer limit) {
        if (processingService == null) {
            return ResponseEntity.internalServerError().build();
        }
        if (limit != null && limit < 1) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.<ProductImageProcessingResult>builder()
                            .success(false)
                            .message("limit must be >= 1. Got: " + limit)
                            .build());
        }
        ProductImageProcessingResult result = productId != null
                ? processingService.processOne(productId)
                : processingService.processAll(limit);
        return ResponseEntity.ok(ApiResponse.success("Processing complete", result));
    }
}
