package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.warehouse.ProductWithStockResponse;
import br.com.stockshift.dto.warehouse.WarehouseRequest;
import br.com.stockshift.dto.warehouse.WarehouseResponse;
import br.com.stockshift.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouses", description = "Warehouse management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_CREATE', 'ROLE_ADMIN')")
    @Operation(summary = "Create a new warehouse")
    public ResponseEntity<ApiResponse<WarehouseResponse>> create(@Valid @RequestBody WarehouseRequest request) {
        WarehouseResponse response = warehouseService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Warehouse created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get all warehouses")
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> findAll() {
        List<WarehouseResponse> warehouses = warehouseService.findAll();
        return ResponseEntity.ok(ApiResponse.success(warehouses));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get warehouse by ID")
    public ResponseEntity<ApiResponse<WarehouseResponse>> findById(@PathVariable UUID id) {
        WarehouseResponse response = warehouseService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/active/{isActive}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get warehouses by active status")
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> findActive(@PathVariable Boolean isActive) {
        List<WarehouseResponse> warehouses = warehouseService.findActive(isActive);
        return ResponseEntity.ok(ApiResponse.success(warehouses));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_UPDATE', 'ROLE_ADMIN')")
    @Operation(summary = "Update warehouse")
    public ResponseEntity<ApiResponse<WarehouseResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody WarehouseRequest request) {
        WarehouseResponse response = warehouseService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Warehouse updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_DELETE', 'ROLE_ADMIN')")
    @Operation(summary = "Delete warehouse")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        warehouseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Warehouse deleted successfully", null));
    }

    @GetMapping("/{id}/products")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get products with stock for warehouse")
    public ResponseEntity<ApiResponse<Page<ProductWithStockResponse>>> getProductsWithStock(
            @PathVariable UUID id,
            Pageable pageable) {
        // Filter out invalid sorts - only allow sorting by entity fields
        Pageable sanitizedPageable = sanitizePageable(pageable);
        Page<ProductWithStockResponse> products = warehouseService.getProductsWithStock(id, sanitizedPageable);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    private Pageable sanitizePageable(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        
        // List of valid sortable fields (Product entity fields only)
        Set<String> validSortFields = Set.of("name", "sku", "barcode", "active", "createdAt", "updatedAt");
        
        List<org.springframework.data.domain.Sort.Order> validOrders = pageable.getSort().stream()
                .filter(order -> validSortFields.contains(order.getProperty()))
                .collect(Collectors.toList());
        
        if (validOrders.isEmpty()) {
            // No valid sort fields, return unsorted
            return org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(), 
                    pageable.getPageSize()
            );
        }
        
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                org.springframework.data.domain.Sort.by(validOrders)
        );
    }
}
