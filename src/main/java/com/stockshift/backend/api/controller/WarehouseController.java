package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.warehouse.CreateWarehouseRequest;
import com.stockshift.backend.api.dto.warehouse.UpdateWarehouseRequest;
import com.stockshift.backend.api.dto.warehouse.WarehouseResponse;
import com.stockshift.backend.api.mapper.WarehouseMapper;
import com.stockshift.backend.application.service.WarehouseService;
import com.stockshift.backend.domain.warehouse.Warehouse;
import com.stockshift.backend.domain.warehouse.WarehouseType;
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
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final WarehouseMapper mapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<WarehouseResponse> createWarehouse(
            @Valid @RequestBody CreateWarehouseRequest request
    ) {
        Warehouse warehouse = warehouseService.createWarehouse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(warehouse));
    }

    @GetMapping
    public ResponseEntity<Page<WarehouseResponse>> getAllWarehouses(
            @RequestParam(value = "onlyActive", required = false, defaultValue = "false") Boolean onlyActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Warehouse> warehouses = onlyActive 
            ? warehouseService.getActiveWarehouses(pageable)
            : warehouseService.getAllWarehouses(pageable);
        return ResponseEntity.ok(warehouses.map(mapper::toResponse));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<Page<WarehouseResponse>> getWarehousesByType(
            @PathVariable(value = "type") WarehouseType type,
            @RequestParam(value = "onlyActive", required = false, defaultValue = "false") Boolean onlyActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Warehouse> warehouses = warehouseService.getWarehousesByType(type, onlyActive, pageable);
        return ResponseEntity.ok(warehouses.map(mapper::toResponse));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<WarehouseResponse>> searchWarehouses(
            @RequestParam(value = "query") String query,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Warehouse> warehouses = warehouseService.searchWarehouses(query, pageable);
        return ResponseEntity.ok(warehouses.map(mapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseResponse> getWarehouseById(
            @PathVariable(value = "id") UUID id
    ) {
        Warehouse warehouse = warehouseService.getWarehouseById(id);
        return ResponseEntity.ok(mapper.toResponse(warehouse));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<WarehouseResponse> getWarehouseByCode(
            @PathVariable(value = "code") String code
    ) {
        Warehouse warehouse = warehouseService.getWarehouseByCode(code);
        return ResponseEntity.ok(mapper.toResponse(warehouse));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<WarehouseResponse> updateWarehouse(
            @PathVariable(value = "id") UUID id,
            @Valid @RequestBody UpdateWarehouseRequest request
    ) {
        Warehouse warehouse = warehouseService.updateWarehouse(id, request);
        return ResponseEntity.ok(mapper.toResponse(warehouse));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateWarehouse(@PathVariable(value = "id") UUID id) {
        warehouseService.deactivateWarehouse(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WarehouseResponse> activateWarehouse(
            @PathVariable(value = "id") UUID id
    ) {
        warehouseService.activateWarehouse(id);
        Warehouse warehouse = warehouseService.getWarehouseById(id);
        return ResponseEntity.ok(mapper.toResponse(warehouse));
    }
}
