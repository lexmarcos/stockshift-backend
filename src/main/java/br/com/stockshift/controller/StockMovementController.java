package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.stockmovement.*;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.service.stockmovement.StockMovementService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-movements")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class StockMovementController {

  private final StockMovementService stockMovementService;

  @PostMapping
  @PreAuthorize("@permissionGuard.hasAny('stock_movements:create')")
  public ResponseEntity<ApiResponse<StockMovementResponse>> create(
      @Valid @RequestBody CreateStockMovementRequest request) {
    StockMovementResponse response = stockMovementService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Stock movement created successfully", response));
  }

  @GetMapping
  @PreAuthorize("@permissionGuard.hasAny('stock_movements:read')")
  public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> list(
      @RequestParam(required = false) UUID warehouseId,
      @RequestParam(required = false) UUID productId,
      @RequestParam(required = false) StockMovementType type,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
      Pageable pageable) {
    Page<StockMovementResponse> response = stockMovementService.list(
        warehouseId, productId, type, dateFrom, dateTo, pageable);
    return ResponseEntity.ok(ApiResponse.success("Stock movements retrieved successfully", response));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@permissionGuard.hasAny('stock_movements:read')")
  public ResponseEntity<ApiResponse<StockMovementResponse>> getById(@PathVariable UUID id) {
    StockMovementResponse response = stockMovementService.getById(id);
    return ResponseEntity.ok(ApiResponse.success("Stock movement retrieved successfully", response));
  }

  @GetMapping("/warehouse-summary")
  @PreAuthorize("@permissionGuard.hasAny('stock_movements:read')")
  public ResponseEntity<ApiResponse<WarehouseMovementSummaryResponse>> warehouseSummary(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
    WarehouseMovementSummaryResponse response = stockMovementService.getWarehouseSummary(dateFrom, dateTo);
    return ResponseEntity.ok(ApiResponse.success("Warehouse summary retrieved successfully", response));
  }
}
