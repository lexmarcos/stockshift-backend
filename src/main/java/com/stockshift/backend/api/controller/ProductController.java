package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.product.CreateProductRequest;
import com.stockshift.backend.api.dto.product.ProductResponse;
import com.stockshift.backend.api.dto.product.UpdateProductRequest;
import com.stockshift.backend.api.mapper.ProductMapper;
import com.stockshift.backend.application.service.ProductService;
import com.stockshift.backend.domain.product.Product;
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
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        Product product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(productMapper.toResponse(product));
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(value = "onlyActive", required = false, defaultValue = "false") Boolean onlyActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Product> products = onlyActive
                ? productService.getActiveProducts(pageable)
                : productService.getAllProducts(pageable);
        return ResponseEntity.ok(products.map(productMapper::toResponse));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam(value = "q") String query,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Product> products = productService.searchProducts(query, pageable);
        return ResponseEntity.ok(products.map(productMapper::toResponse));
    }

    @GetMapping("/brand/{brandId}")
    public ResponseEntity<Page<ProductResponse>> getProductsByBrand(
            @PathVariable(value = "brandId") UUID brandId,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Product> products = productService.getProductsByBrand(brandId, pageable);
        return ResponseEntity.ok(products.map(productMapper::toResponse));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<ProductResponse>> getProductsByCategory(
            @PathVariable(value = "categoryId") UUID categoryId,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Product> products = productService.getProductsByCategory(categoryId, pageable);
        return ResponseEntity.ok(products.map(productMapper::toResponse));
    }

    @GetMapping("/expired")
    public ResponseEntity<Page<ProductResponse>> getExpiredProducts(
            @PageableDefault(size = 20, sort = "expiryDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Product> products = productService.getExpiredProducts(pageable);
        return ResponseEntity.ok(products.map(productMapper::toResponse));
    }

    @GetMapping("/expiring-soon")
    public ResponseEntity<Page<ProductResponse>> getProductsExpiringSoon(
            @RequestParam(value = "days", required = false, defaultValue = "30") Integer days,
            @PageableDefault(size = 20, sort = "expiryDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Product> products = productService.getProductsExpiringSoon(days, pageable);
        return ResponseEntity.ok(products.map(productMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable(value = "id") UUID id) {
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(productMapper.toResponse(product));
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<ProductResponse> getProductByName(@PathVariable(value = "name") String name) {
        Product product = productService.getProductByName(name);
        return ResponseEntity.ok(productMapper.toResponse(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable(value = "id") UUID id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        Product product = productService.updateProduct(id, request);
        return ResponseEntity.ok(productMapper.toResponse(product));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable(value = "id") UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> activateProduct(@PathVariable(value = "id") UUID id) {
        productService.activateProduct(id);
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(productMapper.toResponse(product));
    }
}
