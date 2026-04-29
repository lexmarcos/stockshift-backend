package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.sale.*;
import br.com.stockshift.dto.sale.InfinitePayWebhookRequest;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.Optional;
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
    @PreAuthorize("@permissionGuard.hasAny('sales:create')")
    public ResponseEntity<Void> infinitepayCallback(
            @RequestParam String order_id,
            @RequestParam(value = "warning", required = false) String warning) {

        InfinitePayConfirmResponse response = confirmInfinitePayReturn(order_id, warning);
        return redirectToInfinitePayResult(response.getStatus(), response.getSaleId(), response.getMessage());
    }

    @GetMapping("/infinitepay/confirm")
    @PreAuthorize("@permissionGuard.hasAny('sales:create')")
    public ResponseEntity<ApiResponse<InfinitePayConfirmResponse>> infinitepayConfirm(
            @RequestParam String order_id,
            @RequestParam(value = "warning", required = false) String warning) {

        InfinitePayConfirmResponse response = confirmInfinitePayReturn(order_id, warning);
        return ResponseEntity.ok(ApiResponse.success("InfinitePay payment confirmation processed", response));
    }

    private InfinitePayConfirmResponse confirmInfinitePayReturn(String orderId, String warning) {
        try {
            UUID saleId = UUID.fromString(orderId);

            if (warning != null && !warning.isBlank()) {
                log.warn("InfinitePay callback with warning for sale {}: {}", saleId, warning);
                return buildInfinitePayConfirmResponse("error", saleId.toString(), warning);
            }

            if (saleService.isInfinitePayPaymentCompleted(saleId)) {
                return buildInfinitePayConfirmResponse("success", saleId.toString(), null);
            }
            log.info("InfinitePay callback for sale {} is pending webhook verification", saleId);
            return buildInfinitePayConfirmResponse("error", saleId.toString(), "pending_verification");
        } catch (IllegalArgumentException e) {
            log.warn("Invalid order_id from InfinitePay callback: {}", orderId);
            return buildInfinitePayConfirmResponse("error", null, "invalid_order");
        } catch (Exception e) {
            log.error("Error processing InfinitePay callback for order {}: {}", orderId, e.getMessage());
            return buildInfinitePayConfirmResponse("error", orderId, null);
        }
    }

    private InfinitePayConfirmResponse buildInfinitePayConfirmResponse(
            String status, String saleId, String message) {
        return InfinitePayConfirmResponse.builder()
                .status(status)
                .saleId(saleId)
                .message(message)
                .build();
    }

    private ResponseEntity<Void> redirectToInfinitePayResult(String status, String saleId, String message) {
        return ResponseEntity.status(302)
                .header("Location", buildInfinitePayResultUrl(status, saleId, message))
                .build();
    }

    private String buildInfinitePayResultUrl(String status, String saleId, String message) {
        return UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/sales/infinitepay/result")
                .queryParam("status", status)
                .queryParamIfPresent("sale_id", Optional.ofNullable(saleId))
                .queryParamIfPresent("message", Optional.ofNullable(message))
                .build()
                .encode()
                .toUriString();
    }

    @PostMapping("/infinitepay/webhook/{token}")
    public ResponseEntity<Void> infinitepayWebhook(
            @PathVariable String token,
            @RequestBody InfinitePayWebhookRequest request) {
        try {
            log.info("InfinitePay webhook received for order {} - capture_method: {}",
                    request.getOrder_nsu(), request.getCapture_method());
            saleService.confirmInfinitePayWebhook(token, request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid order_nsu from InfinitePay webhook: {}", request.getOrder_nsu());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing InfinitePay webhook for order {}: {}",
                    request.getOrder_nsu(), e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
