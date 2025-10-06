package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.report.ExpiringItemResponse;
import com.stockshift.backend.api.dto.report.LowStockItemResponse;
import com.stockshift.backend.api.dto.report.StockHistoryEntryResponse;
import com.stockshift.backend.api.dto.report.StockSnapshotItemResponse;
import com.stockshift.backend.api.mapper.ReportMapper;
import com.stockshift.backend.application.service.StockReportService;
import com.stockshift.backend.domain.report.ExpiringItemView;
import com.stockshift.backend.domain.report.LowStockView;
import com.stockshift.backend.domain.report.StockHistoryEntry;
import com.stockshift.backend.domain.report.StockSnapshotView;
import com.stockshift.backend.domain.stock.exception.StockForbiddenException;
import com.stockshift.backend.domain.user.User;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final StockReportService stockReportService;
    private final ReportMapper reportMapper;

    @GetMapping("/stock-snapshot")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SELLER')")
    public ResponseEntity<Page<StockSnapshotItemResponse>> getStockSnapshot(
            @RequestParam(value = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(value = "productId", required = false) UUID productId,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "brandId", required = false) UUID brandId,
            @RequestParam(value = "variantId", required = false) UUID variantId,
            @RequestParam(value = "sku", required = false) String sku,
            @RequestParam(value = "attributeValueIds", required = false) List<UUID> attributeValueIds,
            @RequestParam(value = "aggregate", required = false, defaultValue = "false") boolean aggregate,
            @RequestParam(value = "includeZero", required = false, defaultValue = "false") boolean includeZero,
            @RequestParam(value = "asOf", required = false) OffsetDateTime asOf,
            @PageableDefault(size = 20, sort = "quantity", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        User currentUser = extractUser(authentication);
        Set<UUID> attributes = attributeValueIds != null ? new HashSet<>(attributeValueIds) : Set.of();
        Page<StockSnapshotView> page = stockReportService.getStockSnapshot(
                warehouseId,
                productId,
                categoryId,
                brandId,
                variantId,
                sku,
                attributes,
                aggregate,
                includeZero,
                asOf,
                pageable,
                currentUser
        );
        Page<StockSnapshotItemResponse> response = page.map(reportMapper::toResponse);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stock-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SELLER')")
    public ResponseEntity<Page<StockHistoryEntryResponse>> getStockHistory(
            @RequestParam(value = "variantId", required = false) UUID variantId,
            @RequestParam(value = "productId", required = false) UUID productId,
            @RequestParam(value = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(value = "dateFrom", required = false) OffsetDateTime dateFrom,
            @RequestParam(value = "dateTo", required = false) OffsetDateTime dateTo,
            @RequestParam(value = "attributeValueIds", required = false) List<UUID> attributeValueIds,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        User currentUser = extractUser(authentication);
        Set<UUID> attributes = attributeValueIds != null ? new HashSet<>(attributeValueIds) : Set.of();
        Page<StockHistoryEntry> page = stockReportService.getStockHistory(
                variantId,
                productId,
                warehouseId,
                dateFrom,
                dateTo,
                attributes,
                pageable,
                currentUser
        );
        Page<StockHistoryEntryResponse> response = page.map(reportMapper::toResponse);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SELLER')")
    public ResponseEntity<Page<LowStockItemResponse>> getLowStock(
            @RequestParam(value = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(value = "productId", required = false) UUID productId,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "brandId", required = false) UUID brandId,
            @RequestParam(value = "sku", required = false) String sku,
            @RequestParam(value = "attributeValueIds", required = false) List<UUID> attributeValueIds,
            @RequestParam(value = "threshold", required = false) @Min(1) Long threshold,
            @PageableDefault(size = 20, sort = "deficit", direction = Sort.Direction.ASC) Pageable pageable,
            Authentication authentication
    ) {
        User currentUser = extractUser(authentication);
        Set<UUID> attributes = attributeValueIds != null ? new HashSet<>(attributeValueIds) : Set.of();
        Page<LowStockView> page = stockReportService.getLowStock(
                warehouseId,
                productId,
                categoryId,
                brandId,
                sku,
                attributes,
                threshold,
                pageable,
                currentUser
        );
        Page<LowStockItemResponse> response = page.map(reportMapper::toResponse);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SELLER')")
    public ResponseEntity<Page<ExpiringItemResponse>> getExpiringItems(
            @RequestParam(value = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(value = "productId", required = false) UUID productId,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "brandId", required = false) UUID brandId,
            @RequestParam(value = "sku", required = false) String sku,
            @RequestParam(value = "attributeValueIds", required = false) List<UUID> attributeValueIds,
            @RequestParam(value = "daysAhead", required = false) Integer daysAhead,
            @RequestParam(value = "includeExpired", required = false, defaultValue = "false") boolean includeExpired,
            @RequestParam(value = "aggregate", required = false, defaultValue = "false") boolean aggregate,
            @RequestParam(value = "asOf", required = false) OffsetDateTime asOf,
            @PageableDefault(size = 20, sort = "expiryDate", direction = Sort.Direction.ASC) Pageable pageable,
            Authentication authentication
    ) {
        User currentUser = extractUser(authentication);
        Set<UUID> attributes = attributeValueIds != null ? new HashSet<>(attributeValueIds) : Set.of();
        Page<ExpiringItemView> page = stockReportService.getExpiringItems(
                warehouseId,
                productId,
                categoryId,
                brandId,
                sku,
                attributes,
                daysAhead,
                includeExpired,
                aggregate,
                asOf,
                pageable,
                currentUser
        );
        Page<ExpiringItemResponse> response = page.map(reportMapper::toResponse);
        return ResponseEntity.ok(response);
    }

    private User extractUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new StockForbiddenException();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        throw new StockForbiddenException();
    }
}
