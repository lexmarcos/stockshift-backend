package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.sale.*;
import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.SaleStatus;
import br.com.stockshift.service.sale.SaleService;

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
@RequestMapping("/sales")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    @PreAuthorize("@permissionGuard.hasAny('sales:create')")
    public ResponseEntity<ApiResponse<SaleResponse>> create(
            @Valid @RequestBody CreateSaleRequest request) {
        SaleResponse response = saleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Sale created successfully", response));
    }

    @GetMapping
    @PreAuthorize("@permissionGuard.hasAny('sales:read')")
    public ResponseEntity<ApiResponse<Page<SaleSummaryResponse>>> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            Pageable pageable) {
        Page<SaleSummaryResponse> response = saleService.list(
                warehouseId, paymentMethod, status, dateFrom, dateTo, pageable);
        return ResponseEntity.ok(ApiResponse.success("Sales retrieved successfully", response));
    }

    @GetMapping("/next-code")
    @PreAuthorize("@permissionGuard.hasAny('sales:read')")
    public ResponseEntity<ApiResponse<NextSaleCodeResponse>> getNextCode() {
        NextSaleCodeResponse response = saleService.getNextCode();
        return ResponseEntity.ok(ApiResponse.success("Next code retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionGuard.hasAny('sales:read')")
    public ResponseEntity<ApiResponse<SaleResponse>> getById(@PathVariable UUID id) {
        SaleResponse response = saleService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Sale retrieved successfully", response));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("@permissionGuard.hasAny('sales:cancel')")
    public ResponseEntity<ApiResponse<SaleResponse>> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody CancelSaleRequest request) {
        SaleResponse response = saleService.cancel(id, request);
        return ResponseEntity.ok(ApiResponse.success("Sale cancelled successfully", response));
    }
}
