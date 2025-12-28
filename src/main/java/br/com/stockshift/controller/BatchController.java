package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.warehouse.BatchRequest;
import br.com.stockshift.dto.warehouse.BatchResponse;
import br.com.stockshift.service.BatchService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
@Tag(name = "Batches", description = "Batch and stock management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class BatchController {

    private final BatchService batchService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('BATCH_CREATE', 'ROLE_ADMIN')")
    @Operation(summary = "Create a new batch")
    public ResponseEntity<ApiResponse<BatchResponse>> create(@Valid @RequestBody BatchRequest request) {
        BatchResponse response = batchService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Batch created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('BATCH_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get all batches")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findAll() {
        List<BatchResponse> batches = batchService.findAll();
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('BATCH_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get batch by ID")
    public ResponseEntity<ApiResponse<BatchResponse>> findById(@PathVariable UUID id) {
        BatchResponse response = batchService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @PreAuthorize("hasAnyAuthority('BATCH_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get batches by warehouse")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findByWarehouse(@PathVariable UUID warehouseId) {
        List<BatchResponse> batches = batchService.findByWarehouse(warehouseId);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyAuthority('BATCH_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get batches by product")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findByProduct(@PathVariable UUID productId) {
        List<BatchResponse> batches = batchService.findByProduct(productId);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/expiring/{daysAhead}")
    @PreAuthorize("hasAnyAuthority('BATCH_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get batches expiring in next N days")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findExpiringBatches(@PathVariable Integer daysAhead) {
        List<BatchResponse> batches = batchService.findExpiringBatches(daysAhead);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/low-stock/{threshold}")
    @PreAuthorize("hasAnyAuthority('BATCH_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get batches with quantity below threshold")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findLowStock(@PathVariable Integer threshold) {
        List<BatchResponse> batches = batchService.findLowStock(threshold);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('BATCH_UPDATE', 'ROLE_ADMIN')")
    @Operation(summary = "Update batch")
    public ResponseEntity<ApiResponse<BatchResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody BatchRequest request) {
        BatchResponse response = batchService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Batch updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('BATCH_DELETE', 'ROLE_ADMIN')")
    @Operation(summary = "Delete batch")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        batchService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Batch deleted successfully", null));
    }
}
