package br.com.stockshift.service.transfer;

import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.*;
import br.com.stockshift.repository.InventoryLedgerRepository;
import br.com.stockshift.repository.NewTransferDiscrepancyRepository;
import br.com.stockshift.repository.TransferInTransitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscrepancyService {

    private final NewTransferDiscrepancyRepository discrepancyRepository;
    private final InventoryLedgerRepository inventoryLedgerRepository;
    private final TransferInTransitRepository transferInTransitRepository;

    public record ValidationResult(
        boolean hasDiscrepancy,
        DiscrepancyType discrepancyType,
        BigDecimal difference
    ) {}

    public ValidationResult evaluateItem(TransferItem item) {
        BigDecimal expected = item.getExpectedQuantity();
        BigDecimal received = item.getReceivedQuantity() != null
            ? item.getReceivedQuantity()
            : BigDecimal.ZERO;

        int comparison = received.compareTo(expected);

        if (comparison == 0) {
            return new ValidationResult(false, null, BigDecimal.ZERO);
        } else if (comparison < 0) {
            BigDecimal difference = expected.subtract(received);
            return new ValidationResult(true, DiscrepancyType.SHORTAGE, difference);
        } else {
            BigDecimal difference = received.subtract(expected);
            return new ValidationResult(true, DiscrepancyType.EXCESS, difference);
        }
    }

    public NewTransferDiscrepancy createDiscrepancy(
        Transfer transfer,
        TransferItem item,
        DiscrepancyType type
    ) {
        BigDecimal expected = item.getExpectedQuantity();
        BigDecimal received = item.getReceivedQuantity() != null
            ? item.getReceivedQuantity()
            : BigDecimal.ZERO;
        BigDecimal difference = type == DiscrepancyType.SHORTAGE
            ? expected.subtract(received)
            : received.subtract(expected);

        NewTransferDiscrepancy discrepancy = new NewTransferDiscrepancy();
        discrepancy.setTenantId(transfer.getTenantId());
        discrepancy.setTransfer(transfer);
        discrepancy.setTransferItem(item);
        discrepancy.setDiscrepancyType(type);
        discrepancy.setExpectedQuantity(expected);
        discrepancy.setReceivedQuantity(received);
        discrepancy.setDifference(difference);
        discrepancy.setStatus(DiscrepancyStatus.PENDING_RESOLUTION);

        log.info("Creating {} discrepancy for transfer {} item {}, difference: {}",
            type, transfer.getId(), item.getId(), difference);

        return discrepancyRepository.save(discrepancy);
    }

    @Transactional
    public NewTransferDiscrepancy resolveDiscrepancy(
        UUID discrepancyId,
        DiscrepancyResolution resolution,
        String justification,
        User resolver
    ) {
        NewTransferDiscrepancy discrepancy = discrepancyRepository.findById(discrepancyId)
            .orElseThrow(() -> new ResourceNotFoundException("Discrepancy not found: " + discrepancyId));

        if (discrepancy.getStatus() != DiscrepancyStatus.PENDING_RESOLUTION) {
            throw new BusinessException("Discrepancy is already resolved");
        }

        log.info("Resolving discrepancy {} with {} by user {}",
            discrepancyId, resolution, resolver.getId());

        switch (resolution) {
            case WRITE_OFF -> handleWriteOff(discrepancy, resolver);
            case FOUND -> handleFound(discrepancy, resolver);
            case ACCEPTED -> handleAccepted(discrepancy, resolver);
            case RETURN_TRANSIT -> handleReturnTransit(discrepancy, resolver);
        }

        discrepancy.setResolution(resolution);
        discrepancy.setResolutionNotes(justification);
        discrepancy.setResolvedBy(resolver);
        discrepancy.setResolvedAt(LocalDateTime.now());

        if (resolution == DiscrepancyResolution.WRITE_OFF) {
            discrepancy.setStatus(DiscrepancyStatus.WRITTEN_OFF);
        } else {
            discrepancy.setStatus(DiscrepancyStatus.RESOLVED);
        }

        return discrepancyRepository.save(discrepancy);
    }

    private void handleWriteOff(NewTransferDiscrepancy discrepancy, User resolver) {
        Transfer transfer = discrepancy.getTransfer();
        TransferItem item = discrepancy.getTransferItem();
        BigDecimal lossQuantity = discrepancy.getDifference();

        // Create TRANSFER_LOSS ledger entry
        InventoryLedger lossEntry = new InventoryLedger();
        lossEntry.setTenantId(transfer.getTenantId());
        lossEntry.setWarehouseId(null); // Virtual
        lossEntry.setProductId(item.getProduct().getId());
        lossEntry.setBatchId(null);
        lossEntry.setEntryType(LedgerEntryType.TRANSFER_LOSS);
        lossEntry.setQuantity(lossQuantity);
        lossEntry.setBalanceAfter(null);
        lossEntry.setReferenceType("TRANSFER_DISCREPANCY");
        lossEntry.setReferenceId(discrepancy.getId());
        lossEntry.setTransferItemId(item.getId());
        lossEntry.setCreatedBy(resolver.getId());
        lossEntry.setNotes("Write-off for transfer discrepancy");
        inventoryLedgerRepository.save(lossEntry);

        // Consume remaining transit
        TransferInTransit inTransit = transferInTransitRepository.findByTransferItemId(item.getId())
            .orElse(null);
        if (inTransit != null && inTransit.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            inTransit.setQuantity(BigDecimal.ZERO);
            inTransit.setConsumedAt(LocalDateTime.now());
            transferInTransitRepository.save(inTransit);
        }

        log.info("Written off {} units for discrepancy {}", lossQuantity, discrepancy.getId());
    }

    private void handleFound(NewTransferDiscrepancy discrepancy, User resolver) {
        // FOUND means the item was located - requires manual adjustment
        // For now, just mark as resolved. Manual ADJUSTMENT_IN can be done separately.
        log.info("Discrepancy {} marked as FOUND - manual adjustment may be needed", discrepancy.getId());
    }

    private void handleAccepted(NewTransferDiscrepancy discrepancy, User resolver) {
        // ACCEPTED is for EXCESS type - just mark as resolved with audit flag
        log.info("Excess discrepancy {} accepted", discrepancy.getId());
    }

    private void handleReturnTransit(NewTransferDiscrepancy discrepancy, User resolver) {
        // RETURN_TRANSIT would create a reverse transfer - future implementation
        log.info("Return transit for discrepancy {} - future implementation", discrepancy.getId());
    }

    public List<NewTransferDiscrepancy> findByTransferId(UUID transferId) {
        return discrepancyRepository.findByTransferId(transferId);
    }

    public List<NewTransferDiscrepancy> findPendingByTenantId(UUID tenantId) {
        return discrepancyRepository.findPendingByTenantId(tenantId);
    }

    public NewTransferDiscrepancy findById(UUID id) {
        return discrepancyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Discrepancy not found: " + id));
    }
}
