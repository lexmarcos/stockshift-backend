package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.admin.ProductImageProcessingResult;
import br.com.stockshift.service.ProductImageProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductImageController {

    private final ProductImageProcessingService processingService;

    @PostMapping("/process-images")
    @PreAuthorize("@permissionGuard.has('products:update')")
    public ResponseEntity<ApiResponse<ProductImageProcessingResult>> processImages(
            @RequestParam(required = false) UUID productId) {
        ProductImageProcessingResult result = productId != null
                ? processingService.processOne(productId)
                : processingService.processAll();
        return ResponseEntity.ok(ApiResponse.success("Processing complete", result));
    }
}
