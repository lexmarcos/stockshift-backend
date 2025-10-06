package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.stock.CreateStockEventRequest;
import com.stockshift.backend.api.dto.stock.StockEventResponse;
import com.stockshift.backend.api.mapper.StockEventMapper;
import com.stockshift.backend.application.service.StockEventService;
import com.stockshift.backend.domain.stock.StockEvent;
import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;
import com.stockshift.backend.domain.stock.exception.StockForbiddenException;
import com.stockshift.backend.domain.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-events")
@RequiredArgsConstructor
public class StockEventController {

    private final StockEventService stockEventService;
    private final StockEventMapper stockEventMapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SELLER')")
    public ResponseEntity<StockEventResponse> createStockEvent(
            @Valid @RequestBody CreateStockEventRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        User currentUser = extractUser(authentication);
        StockEvent event = stockEventService.createStockEvent(request, idempotencyKey, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(stockEventMapper.toResponse(event));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SELLER')")
    public ResponseEntity<StockEventResponse> getStockEvent(
            @PathVariable("id") UUID id,
            Authentication authentication) {
        User currentUser = extractUser(authentication);
        StockEvent event = stockEventService.getStockEvent(id, currentUser);
        return ResponseEntity.ok(stockEventMapper.toResponse(event));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SELLER')")
    public ResponseEntity<Page<StockEventResponse>> listStockEvents(
            @RequestParam(value = "type", required = false) StockEventType type,
            @RequestParam(value = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(value = "variantId", required = false) UUID variantId,
            @RequestParam(value = "occurredFrom", required = false) OffsetDateTime occurredFrom,
            @RequestParam(value = "occurredTo", required = false) OffsetDateTime occurredTo,
            @RequestParam(value = "reasonCode", required = false) StockReasonCode reasonCode,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        User currentUser = extractUser(authentication);
        Page<StockEvent> events = stockEventService.listStockEvents(
                type,
                warehouseId,
                variantId,
                occurredFrom,
                occurredTo,
                reasonCode,
                pageable,
                currentUser);
        Page<StockEventResponse> responsePage = events.map(stockEventMapper::toResponse);
        return ResponseEntity.ok(responsePage);
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
