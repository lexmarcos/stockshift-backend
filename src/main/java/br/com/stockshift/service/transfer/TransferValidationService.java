package br.com.stockshift.service.transfer;

import br.com.stockshift.security.TenantContext;
import br.com.stockshift.dto.transfer.*;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.mapper.TransferMapper;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.LedgerEntryType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.*;
import br.com.stockshift.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferValidationService {

    private final TransferRepository transferRepository;
    private final TransferItemRepository transferItemRepository;
    private final TransferValidationLogRepository validationLogRepository;
    private final BatchRepository batchRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryLedgerRepository ledgerRepository;
    private final TransferMapper transferMapper;
    private final TransferStateMachine stateMachine;
    private final SecurityUtils securityUtils;

    @Transactional
    public TransferResponse startValidation(UUID transferId) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        validateDestinationWarehouseAccess(transfer, currentWarehouseId);
        stateMachine.validateTransition(transfer.getStatus(), TransferStatus.PENDING_VALIDATION);

        transfer.setStatus(TransferStatus.PENDING_VALIDATION);
        Transfer saved = transferRepository.save(transfer);

        log.info("Transfer {} validation started", saved.getCode());

        String sourceWarehouseName = warehouseRepository.findById(transfer.getSourceWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");
        String destinationWarehouseName = warehouseRepository.findById(transfer.getDestinationWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");

        return transferMapper.toResponse(saved, sourceWarehouseName, destinationWarehouseName);
    }

    @Transactional
    public ScanBarcodeResponse scanBarcode(UUID transferId, ScanBarcodeRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();
        UUID userId = securityUtils.getCurrentUserId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        validateDestinationWarehouseAccess(transfer, currentWarehouseId);

        if (transfer.getStatus() != TransferStatus.PENDING_VALIDATION) {
            throw new BadRequestException("Transfer must be in PENDING_VALIDATION status to scan");
        }

        String barcode = request.getBarcode().trim();

        // Find matching item
        TransferItem matchingItem = transferItemRepository.findByTransferIdAndProductBarcode(transferId, barcode)
                .orElse(null);

        // Create validation log
        TransferValidationLog logEntry = TransferValidationLog.builder()
                .transferId(transferId)
                .transferItemId(matchingItem != null ? matchingItem.getId() : null)
                .barcode(barcode)
                .validatedByUserId(userId)
                .validatedAt(Instant.now())
                .valid(matchingItem != null)
                .build();
        validationLogRepository.save(logEntry);

        if (matchingItem == null) {
            log.warn("Invalid barcode {} scanned for transfer {}", barcode, transfer.getCode());
            return ScanBarcodeResponse.builder()
                    .valid(false)
                    .message("Product does not belong to this transfer")
                    .build();
        }

        // Increment quantity received
        matchingItem.setQuantityReceived(matchingItem.getQuantityReceived().add(BigDecimal.ONE));
        transferItemRepository.save(matchingItem);

        ScanBarcodeResponse.ScanBarcodeResponseBuilder responseBuilder = ScanBarcodeResponse.builder()
                .valid(true)
                .message("Product registered")
                .productName(matchingItem.getProductName())
                .productBarcode(matchingItem.getProductBarcode())
                .quantitySent(matchingItem.getQuantitySent())
                .quantityReceived(matchingItem.getQuantityReceived());

        if (matchingItem.getQuantityReceived().compareTo(matchingItem.getQuantitySent()) > 0) {
            responseBuilder.warning("Quantity received exceeds quantity sent");
        }

        return responseBuilder.build();
    }

    @Transactional
    public CompleteValidationResponse completeValidation(UUID transferId) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();
        UUID userId = securityUtils.getCurrentUserId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        validateDestinationWarehouseAccess(transfer, currentWarehouseId);

        if (transfer.getStatus() != TransferStatus.PENDING_VALIDATION) {
            throw new BadRequestException("Transfer must be in PENDING_VALIDATION status to complete");
        }

        Warehouse sourceWarehouse = warehouseRepository.findById(transfer.getSourceWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Source warehouse not found"));

        List<CompleteValidationResponse.DiscrepancyItem> discrepancies = new ArrayList<>();
        int itemsOk = 0;
        int itemsWithDiscrepancy = 0;

        for (TransferItem item : transfer.getItems()) {
            // Clear transit quantity from source batch
            Batch sourceBatch = batchRepository.findByIdForUpdate(item.getSourceBatchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source batch not found"));
            sourceBatch.setTransitQuantity(sourceBatch.getTransitQuantity().subtract(item.getQuantitySent()));
            batchRepository.save(sourceBatch);

            // Create destination batch if quantity received > 0
            if (item.getQuantityReceived().compareTo(BigDecimal.ZERO) > 0) {
                Batch destinationBatch = Batch.builder()
                        .product(sourceBatch.getProduct())
                        .warehouse(warehouseRepository.findById(transfer.getDestinationWarehouseId()).orElseThrow())
                        .batchCode(sourceBatch.getBatchCode() + "-TRF-" + transfer.getCode())
                        .quantity(item.getQuantityReceived())
                        .transitQuantity(BigDecimal.ZERO)
                        .costPrice(sourceBatch.getCostPrice())
                        .sellingPrice(sourceBatch.getSellingPrice())
                        .manufacturedDate(sourceBatch.getManufacturedDate())
                        .expirationDate(sourceBatch.getExpirationDate())
                        .originBatch(sourceBatch)
                        .build();
                destinationBatch.setTenantId(tenantId);
                Batch savedBatch = batchRepository.save(destinationBatch);
                item.setDestinationBatchId(savedBatch.getId());

                // Create ledger entry for destination
                LedgerEntryType entryType = item.getQuantityReceived().compareTo(item.getQuantitySent()) == 0
                        ? LedgerEntryType.TRANSFER_IN
                        : LedgerEntryType.TRANSFER_IN_DISCREPANCY;

                InventoryLedger ledgerEntry = InventoryLedger.builder()
                        .tenantId(tenantId)
                        .warehouseId(transfer.getDestinationWarehouseId())
                        .batchId(savedBatch.getId())
                        .productId(item.getProductId())
                        .entryType(entryType)
                        .quantity(item.getQuantityReceived())
                        .referenceType("TRANSFER")
                        .referenceId(transfer.getId())
                        .notes("Transfer from " + sourceWarehouse.getName())
                        .createdBy(userId)
                        .build();
                ledgerRepository.save(ledgerEntry);
            }

            // Check for discrepancy
            BigDecimal difference = item.getQuantityReceived().subtract(item.getQuantitySent());
            if (difference.compareTo(BigDecimal.ZERO) != 0) {
                itemsWithDiscrepancy++;
                discrepancies.add(CompleteValidationResponse.DiscrepancyItem.builder()
                        .productName(item.getProductName())
                        .productBarcode(item.getProductBarcode())
                        .quantitySent(item.getQuantitySent())
                        .quantityReceived(item.getQuantityReceived())
                        .difference(difference)
                        .type(difference.compareTo(BigDecimal.ZERO) < 0
                                ? CompleteValidationResponse.DiscrepancyType.SHORTAGE
                                : CompleteValidationResponse.DiscrepancyType.OVERAGE)
                        .build());
            } else {
                itemsOk++;
            }

            transferItemRepository.save(item);
        }

        // Update transfer status
        TransferStatus finalStatus = discrepancies.isEmpty()
                ? TransferStatus.COMPLETED
                : TransferStatus.COMPLETED_WITH_DISCREPANCY;

        transfer.setStatus(finalStatus);
        transfer.setValidatedByUserId(userId);
        transfer.setValidatedAt(Instant.now());
        transferRepository.save(transfer);

        log.info("Transfer {} completed with status {}", transfer.getCode(), finalStatus);

        return CompleteValidationResponse.builder()
                .transferId(transfer.getId())
                .status(finalStatus)
                .summary(CompleteValidationResponse.ValidationSummary.builder()
                        .totalItemTypes(transfer.getItems().size())
                        .itemsOk(itemsOk)
                        .itemsWithDiscrepancy(itemsWithDiscrepancy)
                        .build())
                .discrepancies(discrepancies)
                .build();
    }

    @Transactional(readOnly = true)
    public DiscrepancyReportResponse getDiscrepancyReport(UUID transferId) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();

        Transfer transfer = transferRepository.findByTenantIdAndIdAndWarehouseScope(tenantId, transferId, currentWarehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        if (transfer.getStatus() != TransferStatus.COMPLETED_WITH_DISCREPANCY) {
            throw new BadRequestException("Discrepancy report only available for transfers with discrepancies");
        }

        String sourceWarehouseName = warehouseRepository.findById(transfer.getSourceWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");
        String destinationWarehouseName = warehouseRepository.findById(transfer.getDestinationWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");

        List<CompleteValidationResponse.DiscrepancyItem> discrepancies = new ArrayList<>();
        BigDecimal totalShortage = BigDecimal.ZERO;
        BigDecimal totalOverage = BigDecimal.ZERO;

        for (TransferItem item : transfer.getItems()) {
            BigDecimal difference = item.getQuantityReceived().subtract(item.getQuantitySent());
            if (difference.compareTo(BigDecimal.ZERO) != 0) {
                CompleteValidationResponse.DiscrepancyType type = difference.compareTo(BigDecimal.ZERO) < 0
                        ? CompleteValidationResponse.DiscrepancyType.SHORTAGE
                        : CompleteValidationResponse.DiscrepancyType.OVERAGE;

                discrepancies.add(CompleteValidationResponse.DiscrepancyItem.builder()
                        .productName(item.getProductName())
                        .productBarcode(item.getProductBarcode())
                        .quantitySent(item.getQuantitySent())
                        .quantityReceived(item.getQuantityReceived())
                        .difference(difference)
                        .type(type)
                        .build());

                if (type == CompleteValidationResponse.DiscrepancyType.SHORTAGE) {
                    totalShortage = totalShortage.add(difference.abs());
                } else {
                    totalOverage = totalOverage.add(difference);
                }
            }
        }

        return DiscrepancyReportResponse.builder()
                .transferId(transfer.getId())
                .transferCode(transfer.getCode())
                .sourceWarehouseName(sourceWarehouseName)
                .destinationWarehouseName(destinationWarehouseName)
                .completedAt(transfer.getValidatedAt())
                .discrepancies(discrepancies)
                .totalShortage(totalShortage)
                .totalOverage(totalOverage)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ValidationLogResponse> getValidationLogs(UUID transferId) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();

        Transfer transfer = transferRepository.findByTenantIdAndIdAndWarehouseScope(tenantId, transferId, currentWarehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        List<TransferValidationLog> logs = validationLogRepository.findAllByTransferId(transferId);
        return transferMapper.toValidationLogResponseList(logs);
    }

    private void validateDestinationWarehouseAccess(Transfer transfer, UUID currentWarehouseId) {
        if (!transfer.getDestinationWarehouseId().equals(currentWarehouseId)) {
            throw new ForbiddenException("Only destination warehouse can perform this action");
        }
    }
}
