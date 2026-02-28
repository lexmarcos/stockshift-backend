package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.dto.product.ProductResponse;
import br.com.stockshift.service.ProductService;
import br.com.stockshift.service.OpenAiService;
import br.com.stockshift.dto.ai.ProductClassificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductController {

    private final ProductService productService;
    private final OpenAiService openAiService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.hasAny('products:create')")
    @Operation(summary = "Create a new product")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @RequestPart("product") @Valid ProductRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        ProductResponse response = productService.create(request, image);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", response));
    }

    @GetMapping
    @PreAuthorize("@permissionGuard.hasAny('products:read')")
    @Operation(summary = "Get all products")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> findAll() {
        List<ProductResponse> products = productService.findAll();
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionGuard.hasAny('products:read')")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ApiResponse<ProductResponse>> findById(@PathVariable UUID id) {
        ProductResponse response = productService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/category/{categoryId}")
    @PreAuthorize("@permissionGuard.hasAny('products:read')")
    @Operation(summary = "Get products by category")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> findByCategory(@PathVariable UUID categoryId) {
        List<ProductResponse> products = productService.findByCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/active/{active}")
    @PreAuthorize("@permissionGuard.hasAny('products:read')")
    @Operation(summary = "Get products by active status")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> findActive(@PathVariable Boolean active) {
        List<ProductResponse> products = productService.findActive(active);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/search")
    @PreAuthorize("@permissionGuard.hasAny('products:read')")
    @Operation(summary = "Search products by name, SKU or barcode")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> search(@RequestParam String q) {
        List<ProductResponse> products = productService.search(q);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/barcode/{barcode}")
    @PreAuthorize("@permissionGuard.hasAny('products:read')")
    @Operation(summary = "Get product by barcode")
    public ResponseEntity<ApiResponse<ProductResponse>> findByBarcode(@PathVariable String barcode) {
        ProductResponse response = productService.findByBarcode(barcode);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/sku/{sku}")
    @PreAuthorize("@permissionGuard.hasAny('products:read')")
    @Operation(summary = "Get product by SKU")
    public ResponseEntity<ApiResponse<ProductResponse>> findBySku(@PathVariable String sku) {
        ProductResponse response = productService.findBySku(sku);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.hasAny('products:update')")
    @Operation(summary = "Update product")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable UUID id,
            @RequestPart("product") @Valid ProductRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        ProductResponse response = productService.update(id, request, image);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionGuard.hasAny('products:delete')")
    @Operation(summary = "Delete product (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }

    @PostMapping(value = "/analyze-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.has('products:analyze_image')")
    @Operation(summary = "Analyze product image using AI")
    public ResponseEntity<ApiResponse<ProductClassificationResponse>> analyzeImage(
            @RequestParam("image") MultipartFile image) throws IOException {
        ProductClassificationResponse response = openAiService.analyzeImage(image);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
