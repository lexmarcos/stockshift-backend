package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.brand.BrandResponse;
import com.stockshift.backend.api.dto.brand.CreateBrandRequest;
import com.stockshift.backend.api.dto.brand.UpdateBrandRequest;
import com.stockshift.backend.api.mapper.BrandMapper;
import com.stockshift.backend.application.service.BrandService;
import com.stockshift.backend.domain.brand.Brand;
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
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;
    private final BrandMapper brandMapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BrandResponse> createBrand(@Valid @RequestBody CreateBrandRequest request) {
        Brand brand = brandService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(brandMapper.toResponse(brand));
    }

    @GetMapping
    public ResponseEntity<Page<BrandResponse>> getAllBrands(
            @RequestParam(required = false, defaultValue = "false") Boolean onlyActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Brand> brands = onlyActive 
            ? brandService.getActiveBrands(pageable)
            : brandService.getAllBrands(pageable);
        return ResponseEntity.ok(brands.map(brandMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BrandResponse> getBrandById(@PathVariable UUID id) {
        Brand brand = brandService.getBrandById(id);
        return ResponseEntity.ok(brandMapper.toResponse(brand));
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<BrandResponse> getBrandByName(@PathVariable String name) {
        Brand brand = brandService.getBrandByName(name);
        return ResponseEntity.ok(brandMapper.toResponse(brand));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BrandResponse> updateBrand(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBrandRequest request
    ) {
        Brand brand = brandService.updateBrand(id, request);
        return ResponseEntity.ok(brandMapper.toResponse(brand));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBrand(@PathVariable UUID id) {
        brandService.deleteBrand(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BrandResponse> activateBrand(@PathVariable UUID id) {
        brandService.activateBrand(id);
        Brand brand = brandService.getBrandById(id);
        return ResponseEntity.ok(brandMapper.toResponse(brand));
    }
}
