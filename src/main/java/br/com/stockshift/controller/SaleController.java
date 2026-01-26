package br.com.stockshift.controller;

import br.com.stockshift.dto.sale.*;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    @PreAuthorize("hasAuthority('SALES:CREATE')")
    public ResponseEntity<SaleResponse> createSale(
            @Valid @RequestBody CreateSaleRequest request,
            @AuthenticationPrincipal User user) {
        
        SaleResponse response = saleService.createSale(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SALES:READ')")
    public ResponseEntity<Page<SaleResponse>> getAllSales(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) 
            Pageable pageable) {
        
        Page<SaleResponse> sales = saleService.getAllSales(user.getTenantId(), pageable);
        return ResponseEntity.ok(sales);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES:READ')")
    public ResponseEntity<SaleResponse> getSaleById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        
        SaleResponse response = saleService.getSaleById(id, user.getTenantId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SALES:CANCEL')")
    public ResponseEntity<SaleResponse> cancelSale(
            @PathVariable Long id,
            @Valid @RequestBody CancelSaleRequest request,
            @AuthenticationPrincipal User user) {
        
        SaleResponse response = saleService.cancelSale(id, request, user);
        return ResponseEntity.ok(response);
    }
}
