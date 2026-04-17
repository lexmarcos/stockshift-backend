package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.sale.*;
import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.SaleStatus;
import br.com.stockshift.service.sale.SaleService;
import br.com.stockshift.service.sale.SalesDashboardService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class SaleController {

    private final SaleService saleService;
    private final SalesDashboardService salesDashboardService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

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

    @GetMapping("/dashboard")
    @PreAuthorize("@permissionGuard.hasAny('sales:read')")
    public ResponseEntity<ApiResponse<SalesDashboardResponse>> dashboard(
            @RequestParam(required = false) UUID warehouseId) {
        SalesDashboardResponse response = salesDashboardService.getDashboard(warehouseId);
        return ResponseEntity.ok(ApiResponse.success("Dashboard retrieved successfully", response));
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

    @GetMapping("/infinitepay/callback")
    public ResponseEntity<Void> infinitepayCallback(
            @RequestParam String order_id,
            @RequestParam(required = false) String nsu,
            @RequestParam(required = false) String aut,
            @RequestParam(required = false) String card_brand,
            @RequestParam(value = "warning", required = false) String warning) {

        try {
            UUID saleId = UUID.fromString(order_id);

            if (warning != null && !warning.isBlank()) {
                log.warn("InfinitePay callback with warning for sale {}: {}", saleId, warning);
                return ResponseEntity.status(302)
                        .header("Location", frontendUrl + "/sales/pdv?infinitepay=error&sale_id=" + saleId + "&message=" + URLEncoder.encode(warning, StandardCharsets.UTF_8))
                        .build();
            }

            saleService.confirmInfinitePayPayment(saleId, nsu, aut, card_brand);
            return ResponseEntity.status(302)
                    .header("Location", frontendUrl + "/sales/pdv?infinitepay=success&sale_id=" + saleId)
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid order_id from InfinitePay callback: {}", order_id);
            return ResponseEntity.status(302)
                    .header("Location", frontendUrl + "/sales/pdv?infinitepay=error&message=invalid_order")
                    .build();
        } catch (Exception e) {
            log.error("Error processing InfinitePay callback for order {}: {}", order_id, e.getMessage());
            return ResponseEntity.status(302)
                    .header("Location", frontendUrl + "/sales/pdv?infinitepay=error&sale_id=" + order_id)
                    .build();
        }
    }
}
