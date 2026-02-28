package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.service.transfer.TransferService;
import br.com.stockshift.service.transfer.TransferValidationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class TransferController {

    private final TransferService transferService;
    private final TransferValidationService validationService;

    @PostMapping
    @PreAuthorize("@permissionGuard.hasAny('transfers:create')")
    public ResponseEntity<ApiResponse<TransferResponse>> create(@Valid @RequestBody CreateTransferRequest request) {
        TransferResponse response = transferService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transfer created successfully", response));
    }

    @GetMapping
    @PreAuthorize("@permissionGuard.hasAny('transfers:read')")
    public ResponseEntity<ApiResponse<Page<TransferResponse>>> list(
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) UUID sourceWarehouseId,
            @RequestParam(required = false) UUID destinationWarehouseId,
            Pageable pageable) {
        Page<TransferResponse> response = transferService.list(status, sourceWarehouseId, destinationWarehouseId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Transfers retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionGuard.hasAny('transfers:read')")
    public ResponseEntity<ApiResponse<TransferResponse>> getById(@PathVariable UUID id) {
        TransferResponse response = transferService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Transfer retrieved successfully", response));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@permissionGuard.hasAny('transfers:update')")
    public ResponseEntity<ApiResponse<TransferResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransferRequest request) {
        TransferResponse response = transferService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Transfer updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionGuard.hasAny('transfers:delete')")
    public ResponseEntity<ApiResponse<TransferResponse>> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelTransferRequest request) {
        TransferResponse response = transferService.cancel(id, request != null ? request : new CancelTransferRequest());
        return ResponseEntity.ok(ApiResponse.success("Transfer cancelled successfully", response));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("@permissionGuard.hasAny('transfers:execute')")
    public ResponseEntity<ApiResponse<TransferResponse>> execute(@PathVariable UUID id) {
        TransferResponse response = transferService.execute(id);
        return ResponseEntity.ok(ApiResponse.success("Transfer executed successfully", response));
    }

    @PostMapping("/{id}/start-validation")
    @PreAuthorize("@permissionGuard.hasAny('transfers:validate')")
    public ResponseEntity<ApiResponse<TransferResponse>> startValidation(@PathVariable UUID id) {
        TransferResponse response = validationService.startValidation(id);
        return ResponseEntity.ok(ApiResponse.success("Validation started successfully", response));
    }

    @PostMapping("/{id}/scan")
    @PreAuthorize("@permissionGuard.hasAny('transfers:validate')")
    public ResponseEntity<ApiResponse<ScanBarcodeResponse>> scanBarcode(
            @PathVariable UUID id,
            @Valid @RequestBody ScanBarcodeRequest request) {
        ScanBarcodeResponse response = validationService.scanBarcode(id, request);
        return ResponseEntity.ok(ApiResponse.success("Barcode processed", response));
    }

    @PostMapping("/{id}/complete-validation")
    @PreAuthorize("@permissionGuard.hasAny('transfers:validate')")
    public ResponseEntity<ApiResponse<CompleteValidationResponse>> completeValidation(@PathVariable UUID id) {
        CompleteValidationResponse response = validationService.completeValidation(id);
        return ResponseEntity.ok(ApiResponse.success("Validation completed successfully", response));
    }

    @GetMapping("/{id}/discrepancy-report")
    @PreAuthorize("@permissionGuard.hasAny('transfers:read')")
    public ResponseEntity<ApiResponse<DiscrepancyReportResponse>> getDiscrepancyReport(@PathVariable UUID id) {
        DiscrepancyReportResponse response = validationService.getDiscrepancyReport(id);
        return ResponseEntity.ok(ApiResponse.success("Discrepancy report retrieved successfully", response));
    }

    @GetMapping("/{id}/validation-logs")
    @PreAuthorize("@permissionGuard.hasAny('transfers:read')")
    public ResponseEntity<ApiResponse<List<ValidationLogResponse>>> getValidationLogs(@PathVariable UUID id) {
        List<ValidationLogResponse> response = validationService.getValidationLogs(id);
        return ResponseEntity.ok(ApiResponse.success("Validation logs retrieved successfully", response));
    }
}
