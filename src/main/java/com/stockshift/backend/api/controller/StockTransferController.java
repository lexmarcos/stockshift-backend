package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.transfer.CreateTransferRequest;
import com.stockshift.backend.api.dto.transfer.TransferResponse;
import com.stockshift.backend.api.mapper.StockTransferMapper;
import com.stockshift.backend.application.service.StockTransferService;
import com.stockshift.backend.domain.stock.StockTransfer;
import com.stockshift.backend.domain.stock.TransferStatus;
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
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService transferService;
    private final StockTransferMapper transferMapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TransferResponse> createDraft(
            @Valid @RequestBody CreateTransferRequest request,
            Authentication authentication
    ) {
        User currentUser = extractUser(authentication);
        StockTransfer transfer = transferService.createDraft(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(transferMapper.toResponse(transfer));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TransferResponse> confirmTransfer(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        User currentUser = extractUser(authentication);
        StockTransfer transfer = transferService.confirmTransfer(id, idempotencyKey, currentUser);
        return ResponseEntity.ok(transferMapper.toResponse(transfer));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TransferResponse> cancelDraft(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        User currentUser = extractUser(authentication);
        StockTransfer transfer = transferService.cancelDraft(id, currentUser);
        return ResponseEntity.ok(transferMapper.toResponse(transfer));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SELLER')")
    public ResponseEntity<TransferResponse> getTransfer(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        User currentUser = extractUser(authentication);
        StockTransfer transfer = transferService.getTransfer(id, currentUser);
        return ResponseEntity.ok(transferMapper.toResponse(transfer));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SELLER')")
    public ResponseEntity<Page<TransferResponse>> listTransfers(
            @RequestParam(value = "status", required = false) TransferStatus status,
            @RequestParam(value = "originWarehouseId", required = false) UUID originWarehouseId,
            @RequestParam(value = "destinationWarehouseId", required = false) UUID destinationWarehouseId,
            @RequestParam(value = "occurredFrom", required = false) OffsetDateTime occurredFrom,
            @RequestParam(value = "occurredTo", required = false) OffsetDateTime occurredTo,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        User currentUser = extractUser(authentication);
        Page<StockTransfer> transfers = transferService.listTransfers(
                status,
                originWarehouseId,
                destinationWarehouseId,
                occurredFrom,
                occurredTo,
                pageable,
                currentUser
        );
        Page<TransferResponse> responsePage = transfers.map(transferMapper::toResponse);
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
