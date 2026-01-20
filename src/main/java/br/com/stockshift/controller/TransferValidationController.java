package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.validation.*;
import br.com.stockshift.service.TransferValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/stock-movements/{movementId}/validations")
@RequiredArgsConstructor
@Tag(name = "Transfer Validations", description = "Transfer validation endpoints for barcode scanning")
@SecurityRequirement(name = "Bearer Authentication")
public class TransferValidationController {

    private final TransferValidationService validationService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_EXECUTE', 'ROLE_ADMIN')")
    @Operation(summary = "Start a new validation for a transfer")
    public ResponseEntity<ApiResponse<StartValidationResponse>> startValidation(
            @PathVariable UUID movementId) {
        StartValidationResponse response = validationService.startValidation(movementId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Validation started successfully", response));
    }

    @PostMapping("/{validationId}/scan")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_EXECUTE', 'ROLE_ADMIN')")
    @Operation(summary = "Scan a barcode during validation")
    public ResponseEntity<ApiResponse<ScanResponse>> scanBarcode(
            @PathVariable UUID movementId,
            @PathVariable UUID validationId,
            @Valid @RequestBody ScanRequest request) {
        ScanResponse response = validationService.scanBarcode(movementId, validationId, request.getBarcode());
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @GetMapping("/{validationId}")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_READ', 'ROLE_ADMIN')")
    @Operation(summary = "Get validation progress")
    public ResponseEntity<ApiResponse<ValidationProgressResponse>> getProgress(
            @PathVariable UUID movementId,
            @PathVariable UUID validationId) {
        ValidationProgressResponse response = validationService.getProgress(movementId, validationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{validationId}/complete")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_EXECUTE', 'ROLE_ADMIN')")
    @Operation(summary = "Complete a validation")
    public ResponseEntity<ApiResponse<CompleteValidationResponse>> completeValidation(
            @PathVariable UUID movementId,
            @PathVariable UUID validationId) {
        CompleteValidationResponse response = validationService.completeValidation(movementId, validationId);
        return ResponseEntity.ok(ApiResponse.success("Validation completed successfully", response));
    }
}
