package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.warehouse.BatchDeletionResponse;
import br.com.stockshift.dto.warehouse.BatchRequest;
import br.com.stockshift.dto.warehouse.BatchResponse;
import br.com.stockshift.dto.warehouse.ProductBatchRequest;
import br.com.stockshift.dto.warehouse.ProductBatchResponse;
import br.com.stockshift.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
@Tag(name = "Batches", description = "Batch and stock management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class BatchController {

    private final BatchService batchService;

    @PostMapping
    @PreAuthorize("@permissionGuard.hasAny('batches:create')")
    @Operation(summary = "Create a new batch")
    public ResponseEntity<ApiResponse<BatchResponse>> create(@Valid @RequestBody BatchRequest request) {
        BatchResponse response = batchService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Batch created successfully", response));
    }

    @PostMapping(value = "/with-product", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.hasAny('batches:create', 'products:create')")
    @Operation(summary = "Create a new product with initial stock in warehouse")
    public ResponseEntity<ApiResponse<ProductBatchResponse>> createWithProduct(
            @RequestPart("product") @Valid ProductBatchRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        ProductBatchResponse response = batchService.createWithProduct(request, image);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product and batch created successfully", response));
    }

    @GetMapping
    @PreAuthorize("@permissionGuard.hasAny('batches:read')")
    @Operation(summary = "Get all batches")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findAll() {
        List<BatchResponse> batches = batchService.findAll();
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionGuard.hasAny('batches:read')")
    @Operation(summary = "Get batch by ID")
    public ResponseEntity<ApiResponse<BatchResponse>> findById(@PathVariable UUID id) {
        BatchResponse response = batchService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @PreAuthorize("@permissionGuard.has('batches:read') and @warehouseGuard.isCurrent(#warehouseId)")
    @Operation(summary = "Get batches by warehouse")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findByWarehouse(@PathVariable UUID warehouseId) {
        List<BatchResponse> batches = batchService.findByWarehouse(warehouseId);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("@permissionGuard.hasAny('batches:read')")
    @Operation(summary = "Get batches by product")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findByProduct(@PathVariable UUID productId) {
        List<BatchResponse> batches = batchService.findByProduct(productId);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/warehouses/{warehouseId}/products/{productId}/batches")
    @PreAuthorize("@permissionGuard.has('batches:read') and @warehouseGuard.isCurrent(#warehouseId)")
    @Operation(summary = "Get batches by warehouse and product")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findByWarehouseAndProduct(
        @PathVariable UUID warehouseId,
        @PathVariable UUID productId
    ) {
        List<BatchResponse> batches = batchService.findByWarehouseAndProduct(warehouseId, productId);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/expiring/{daysAhead}")
    @PreAuthorize("@permissionGuard.hasAny('batches:read')")
    @Operation(summary = "Get batches expiring in next N days")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findExpiringBatches(@PathVariable Integer daysAhead) {
        List<BatchResponse> batches = batchService.findExpiringBatches(daysAhead);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @GetMapping("/low-stock/{threshold}")
    @PreAuthorize("@permissionGuard.hasAny('batches:read')")
    @Operation(summary = "Get batches with quantity below threshold")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> findLowStock(@PathVariable Integer threshold) {
        List<BatchResponse> batches = batchService.findLowStock(threshold);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionGuard.hasAny('batches:update')")
    @Operation(summary = "Update batch")
    public ResponseEntity<ApiResponse<BatchResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody BatchRequest request) {
        BatchResponse response = batchService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Batch updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionGuard.hasAny('batches:delete')")
    @Operation(summary = "Delete batch")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        batchService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Batch deleted successfully", null));
    }

    @DeleteMapping("/warehouses/{warehouseId}/products/{productId}/batches")
    @PreAuthorize("@permissionGuard.has('batches:delete') and @warehouseGuard.isCurrent(#warehouseId)")
    public ResponseEntity<BatchDeletionResponse> deleteAllBatchesByProductAndWarehouse(
        @PathVariable UUID warehouseId,
        @PathVariable UUID productId
    ) {
        log.info("Request to delete all batches for product {} in warehouse {}",
            productId, warehouseId);

        BatchDeletionResponse response = batchService.deleteAllByProductAndWarehouse(
            warehouseId, productId);

        return ResponseEntity.ok(response);
    }
}
