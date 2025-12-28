package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.movement.StockMovementRequest;
import br.com.stockshift.dto.movement.StockMovementResponse;
import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.model.enums.MovementType;
import br.com.stockshift.service.StockMovementService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/stock-movements")
@RequiredArgsConstructor
@Tag(name = "Stock Movements", description = "Stock movement management endpoints")
public class StockMovementController {

    private final StockMovementService stockMovementService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_CREATE', 'ADMIN')")
    @Operation(summary = "Create a new stock movement")
    public ResponseEntity<ApiResponse<StockMovementResponse>> create(@Valid @RequestBody StockMovementRequest request) {
        StockMovementResponse response = stockMovementService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock movement created successfully", response));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_EXECUTE', 'ADMIN')")
    @Operation(summary = "Execute a pending stock movement")
    public ResponseEntity<ApiResponse<StockMovementResponse>> execute(@PathVariable UUID id) {
        StockMovementResponse response = stockMovementService.executeMovement(id);
        return ResponseEntity.ok(ApiResponse.success("Stock movement executed successfully", response));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_UPDATE', 'ADMIN')")
    @Operation(summary = "Cancel a stock movement")
    public ResponseEntity<ApiResponse<StockMovementResponse>> cancel(@PathVariable UUID id) {
        StockMovementResponse response = stockMovementService.cancelMovement(id);
        return ResponseEntity.ok(ApiResponse.success("Stock movement cancelled successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_READ', 'ADMIN')")
    @Operation(summary = "Get all stock movements")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> findAll() {
        List<StockMovementResponse> movements = stockMovementService.findAll();
        return ResponseEntity.ok(ApiResponse.success(movements));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_READ', 'ADMIN')")
    @Operation(summary = "Get stock movement by ID")
    public ResponseEntity<ApiResponse<StockMovementResponse>> findById(@PathVariable UUID id) {
        StockMovementResponse response = stockMovementService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/type/{movementType}")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_READ', 'ADMIN')")
    @Operation(summary = "Get stock movements by type")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> findByType(@PathVariable MovementType movementType) {
        List<StockMovementResponse> movements = stockMovementService.findByType(movementType);
        return ResponseEntity.ok(ApiResponse.success(movements));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_READ', 'ADMIN')")
    @Operation(summary = "Get stock movements by status")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> findByStatus(@PathVariable MovementStatus status) {
        List<StockMovementResponse> movements = stockMovementService.findByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(movements));
    }
}
