package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.mapper.TransferMapper;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.NewTransferDiscrepancy;
import br.com.stockshift.model.enums.DiscrepancyResolution;
import br.com.stockshift.model.enums.TransferAction;
import br.com.stockshift.model.enums.TransferRole;
import br.com.stockshift.service.transfer.TransferHistoryService;
import br.com.stockshift.service.transfer.TransferSecurityService;
import br.com.stockshift.service.transfer.TransferService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stockshift/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final br.com.stockshift.service.transfer.DiscrepancyService discrepancyService;
    private final br.com.stockshift.service.LedgerQueryService ledgerQueryService;
    private final TransferSecurityService securityService;
    private final TransferHistoryService historyService;
    private final TransferMapper mapper;

    @PostMapping
    @PreAuthorize("hasAuthority('TRANSFER:CREATE')")
    public ResponseEntity<TransferResponse> createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.createTransfer(request, user);
        TransferRole role = securityService.determineUserRole(transfer);
        List<TransferAction> actions = calculateAllowedActions(transfer, role);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapper.toResponse(transfer, role, actions));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSFER:READ')")
    public ResponseEntity<TransferResponse> getTransfer(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.getTransfer(id);
        TransferRole role = securityService.determineUserRole(transfer);
        List<TransferAction> actions = calculateAllowedActions(transfer, role);

        return ResponseEntity.ok(mapper.toResponse(transfer, role, actions));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSFER:UPDATE')")
    public ResponseEntity<TransferResponse> updateTransfer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransferRequest request,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.updateTransfer(id, request, user);
        TransferRole role = securityService.determineUserRole(transfer);
        List<TransferAction> actions = calculateAllowedActions(transfer, role);

        return ResponseEntity.ok(mapper.toResponse(transfer, role, actions));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSFER:DELETE')")
    public ResponseEntity<Void> cancelTransfer(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal User user) {

        transferService.cancel(id, reason, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('TRANSFER:EXECUTE')")
    public ResponseEntity<TransferResponse> dispatchTransfer(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.dispatch(id, user);
        TransferRole role = securityService.determineUserRole(transfer);
        List<TransferAction> actions = calculateAllowedActions(transfer, role);

        return ResponseEntity.ok(mapper.toResponse(transfer, role, actions));
    }

    @PostMapping("/{id}/validation/start")
    @PreAuthorize("hasAuthority('TRANSFER:VALIDATE')")
    public ResponseEntity<TransferValidationResponse> startValidation(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.startValidation(id, user);
        return ResponseEntity.ok(buildValidationResponse(transfer));
    }

    @PostMapping("/{id}/validation/scan")
    @PreAuthorize("hasAuthority('TRANSFER:VALIDATE')")
    public ResponseEntity<TransferValidationResponse> scanItem(
            @PathVariable UUID id,
            @Valid @RequestBody ScanItemRequest request,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.scanItem(id, request, user);
        return ResponseEntity.ok(buildValidationResponse(transfer));
    }

    @PostMapping("/{id}/validation/complete")
    @PreAuthorize("hasAuthority('TRANSFER:VALIDATE')")
    public ResponseEntity<TransferResponse> completeValidation(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.completeValidation(id, user);
        TransferRole role = securityService.determineUserRole(transfer);
        List<TransferAction> actions = calculateAllowedActions(transfer, role);

        return ResponseEntity.ok(mapper.toResponse(transfer, role, actions));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TRANSFER:READ')")
    public ResponseEntity<Page<TransferSummaryResponse>> listTransfers(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String direction,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User user) {

        Page<Transfer> transfers = transferService.listTransfers(warehouseId, status, direction, pageable, user);

        Page<TransferSummaryResponse> response = transfers.map(transfer -> {
            TransferRole role = securityService.determineUserRole(transfer);
            List<TransferAction> actions = calculateAllowedActions(transfer, role);
            return mapper.toSummaryResponse(transfer, role, actions);
        });

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/discrepancies")
    @PreAuthorize("hasAuthority('TRANSFER:READ')")
    public ResponseEntity<List<DiscrepancyResponse>> listDiscrepancies(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        List<NewTransferDiscrepancy> discrepancies = discrepancyService.findByTransferId(id);
        return ResponseEntity.ok(discrepancies.stream()
                .map(this::toDiscrepancyResponse)
                .toList());
    }

    @PostMapping("/discrepancies/{discrepancyId}/resolve")
    @PreAuthorize("hasAuthority('TRANSFER:VALIDATE')")
    public ResponseEntity<DiscrepancyResponse> resolveDiscrepancy(
            @PathVariable UUID discrepancyId,
            @RequestParam DiscrepancyResolution resolution,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal User user) {

        NewTransferDiscrepancy discrepancy = discrepancyService.resolveDiscrepancy(discrepancyId, resolution, notes, user);
        return ResponseEntity.ok(toDiscrepancyResponse(discrepancy));
    }

    @GetMapping("/{id}/ledger")
    @PreAuthorize("hasAuthority('TRANSFER:READ')")
    public ResponseEntity<List<LedgerEntryResponse>> getTransferLedger(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        List<br.com.stockshift.model.entity.InventoryLedger> ledgerEntries = ledgerQueryService.findByTransferId(id);

        return ResponseEntity.ok(ledgerEntries.stream()
            .map(this::toLedgerEntryResponse)
            .toList());
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('TRANSFER:READ')")
    public ResponseEntity<ApiResponse<TransferHistoryResponse>> getTransferHistory(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        Transfer transfer = transferService.getTransfer(id);
        TransferHistoryResponse history = historyService.getHistory(transfer);

        return ResponseEntity.ok(ApiResponse.success(history));
    }

    private LedgerEntryResponse toLedgerEntryResponse(br.com.stockshift.model.entity.InventoryLedger entry) {
        return new LedgerEntryResponse(
            entry.getId(),
            entry.getWarehouseId(),
            entry.getProductId(),
            entry.getBatchId(),
            entry.getEntryType(),
            entry.getEntryType().isDebit(),
            entry.getQuantity(),
            entry.getBalanceAfter(),
            entry.getReferenceType(),
            entry.getReferenceId(),
            entry.getTransferItemId(),
            entry.getNotes(),
            entry.getCreatedBy(),
            entry.getCreatedAt()
        );
    }

    private DiscrepancyResponse toDiscrepancyResponse(NewTransferDiscrepancy discrepancy) {
        return new DiscrepancyResponse(
                discrepancy.getId(),
                discrepancy.getTransfer().getId(),
                discrepancy.getTransferItem().getId(),
                discrepancy.getDiscrepancyType(),
                discrepancy.getExpectedQuantity(),
                discrepancy.getReceivedQuantity(),
                discrepancy.getDifference(),
                discrepancy.getStatus(),
                discrepancy.getResolution(),
                discrepancy.getResolutionNotes(),
                discrepancy.getResolvedBy() != null ? discrepancy.getResolvedBy().getId() : null,
                discrepancy.getResolvedAt(),
                discrepancy.getCreatedAt()
        );
    }

    private List<TransferAction> calculateAllowedActions(Transfer transfer, TransferRole role) {
        return transferService.calculateAllowedActions(transfer, role);
    }

    private TransferValidationResponse buildValidationResponse(Transfer transfer) {
        TransferRole role = securityService.determineUserRole(transfer);
        List<TransferAction> actions = calculateAllowedActions(transfer, role);

        int itemsScanned = (int) transfer.getItems().stream()
            .filter(i -> i.getReceivedQuantity() != null && i.getReceivedQuantity().compareTo(java.math.BigDecimal.ZERO) > 0)
            .count();

        boolean hasDiscrepancy = transfer.getItems().stream()
            .anyMatch(i -> i.getReceivedQuantity() != null &&
                i.getReceivedQuantity().compareTo(i.getExpectedQuantity()) != 0);

        return TransferValidationResponse.builder()
            .transferId(transfer.getId())
            .status(transfer.getStatus())
            .validationStartedAt(transfer.getValidationStartedAt())
            .validationStartedBy(mapper.toUserSummary(transfer.getValidationStartedBy()))
            .items(transfer.getItems().stream().map(mapper::toItemResponse).toList())
            .totalItems(transfer.getItems().size())
            .itemsScanned(itemsScanned)
            .itemsPending(transfer.getItems().size() - itemsScanned)
            .hasDiscrepancy(hasDiscrepancy)
            .canComplete(itemsScanned > 0)
            .allowedActions(actions)
            .build();
    }
}
