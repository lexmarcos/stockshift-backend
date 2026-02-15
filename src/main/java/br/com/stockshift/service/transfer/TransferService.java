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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private static final int TRANSFER_CODE_SEQUENCE_PADDING = 4;

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
    public TransferResponse create(CreateTransferRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID sourceWarehouseId = securityUtils.getCurrentWarehouseId();
        UUID userId = securityUtils.getCurrentUserId();

        // Validate destination warehouse
        if (sourceWarehouseId.equals(request.getDestinationWarehouseId())) {
            throw new BadRequestException("Source and destination warehouses must be different");
        }

        Warehouse destinationWarehouse = warehouseRepository.findByTenantIdAndId(tenantId, request.getDestinationWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination warehouse not found"));

        Warehouse sourceWarehouse = warehouseRepository.findByTenantIdAndId(tenantId, sourceWarehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Source warehouse not found"));

        // Generate transfer code
        String code = generateTransferCode(tenantId);

        // Create transfer
        Transfer transfer = Transfer.builder()
                .code(code)
                .sourceWarehouseId(sourceWarehouseId)
                .destinationWarehouseId(request.getDestinationWarehouseId())
                .status(TransferStatus.DRAFT)
                .notes(request.getNotes())
                .createdByUserId(userId)
                .build();
        transfer.setTenantId(tenantId);

        // Add items
        for (CreateTransferItemRequest itemRequest : request.getItems()) {
            TransferItem item = createTransferItem(tenantId, sourceWarehouseId, itemRequest);
            transfer.addItem(item);
        }

        Transfer saved = transferRepository.save(transfer);
        log.info("Transfer {} created by user {}", saved.getCode(), userId);

        return transferMapper.toResponse(saved, sourceWarehouse.getName(), destinationWarehouse.getName());
    }

    private TransferItem createTransferItem(UUID tenantId, UUID sourceWarehouseId, CreateTransferItemRequest request) {
        Batch batch = batchRepository.findByTenantIdAndId(tenantId, request.getSourceBatchId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + request.getSourceBatchId()));

        if (!batch.getWarehouse().getId().equals(sourceWarehouseId)) {
            throw new BadRequestException("Batch does not belong to source warehouse");
        }

        if (batch.getQuantity().compareTo(request.getQuantity()) < 0) {
            throw new BadRequestException("Insufficient quantity in batch " + batch.getBatchCode());
        }

        Product product = batch.getProduct();

        return TransferItem.builder()
                .sourceBatchId(batch.getId())
                .productId(product.getId())
                .productBarcode(product.getBarcode())
                .productName(product.getName())
                .productSku(product.getSku())
                .quantitySent(request.getQuantity())
                .quantityReceived(BigDecimal.ZERO)
                .build();
    }

    private String generateTransferCode(UUID tenantId) {
        String prefix = "TRF-" + LocalDate.now().getYear() + "-";
        String latestCode = transferRepository.findLatestCodeByTenantIdAndCodePrefix(tenantId, prefix);

        if (latestCode == null || latestCode.isBlank()) {
            return prefix + String.format("%0" + TRANSFER_CODE_SEQUENCE_PADDING + "d", 1);
        }

        String sequencePart = latestCode.substring(prefix.length());
        try {
            long nextSequence = Long.parseLong(sequencePart) + 1;
            return prefix + String.format("%0" + TRANSFER_CODE_SEQUENCE_PADDING + "d", nextSequence);
        } catch (NumberFormatException ex) {
            log.warn("Unexpected transfer code format '{}' for tenant {}. Falling back to count-based sequence.",
                    latestCode, tenantId);
            long count = transferRepository.countByTenantIdAndCodePrefix(tenantId, prefix);
            return prefix + String.format("%0" + TRANSFER_CODE_SEQUENCE_PADDING + "d", count + 1);
        }
    }

    @Transactional(readOnly = true)
    public TransferResponse getById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        String sourceWarehouseName = warehouseRepository.findById(transfer.getSourceWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");
        String destinationWarehouseName = warehouseRepository.findById(transfer.getDestinationWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");

        return transferMapper.toResponse(transfer, sourceWarehouseName, destinationWarehouseName);
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> list(TransferStatus status, UUID sourceWarehouseId, UUID destinationWarehouseId, Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();

        Page<Transfer> transfers;
        if (status != null) {
            transfers = transferRepository.findAllByTenantIdAndStatus(tenantId, status, pageable);
        } else if (sourceWarehouseId != null) {
            transfers = transferRepository.findAllByTenantIdAndSourceWarehouseId(tenantId, sourceWarehouseId, pageable);
        } else if (destinationWarehouseId != null) {
            transfers = transferRepository.findAllByTenantIdAndDestinationWarehouseId(tenantId, destinationWarehouseId, pageable);
        } else {
            transfers = transferRepository.findAllByTenantId(tenantId, pageable);
        }

        return transfers.map(t -> {
            String sourceName = warehouseRepository.findById(t.getSourceWarehouseId())
                    .map(Warehouse::getName).orElse("Unknown");
            String destName = warehouseRepository.findById(t.getDestinationWarehouseId())
                    .map(Warehouse::getName).orElse("Unknown");
            return transferMapper.toResponse(t, sourceName, destName);
        });
    }

    @Transactional
    public TransferResponse update(UUID id, UpdateTransferRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        validateSourceWarehouseAccess(transfer, currentWarehouseId);

        if (transfer.getStatus() != TransferStatus.DRAFT) {
            throw new BadRequestException("Can only update transfers in DRAFT status");
        }

        if (request.getNotes() != null) {
            transfer.setNotes(request.getNotes());
        }

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            transfer.getItems().clear();
            for (CreateTransferItemRequest itemRequest : request.getItems()) {
                TransferItem item = createTransferItem(tenantId, currentWarehouseId, itemRequest);
                transfer.addItem(item);
            }
        }

        Transfer saved = transferRepository.save(transfer);

        String sourceWarehouseName = warehouseRepository.findById(transfer.getSourceWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");
        String destinationWarehouseName = warehouseRepository.findById(transfer.getDestinationWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");

        return transferMapper.toResponse(saved, sourceWarehouseName, destinationWarehouseName);
    }

    @Transactional
    public TransferResponse execute(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();
        UUID userId = securityUtils.getCurrentUserId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        validateSourceWarehouseAccess(transfer, currentWarehouseId);
        stateMachine.validateTransition(transfer.getStatus(), TransferStatus.IN_TRANSIT);

        Warehouse destinationWarehouse = warehouseRepository.findById(transfer.getDestinationWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination warehouse not found"));

        // Process each item: validate stock, update batch quantities, create ledger entries
        for (TransferItem item : transfer.getItems()) {
            Batch batch = batchRepository.findByIdForUpdate(item.getSourceBatchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + item.getSourceBatchId()));

            if (batch.getQuantity().compareTo(item.getQuantitySent()) < 0) {
                throw new BadRequestException("Insufficient quantity in batch " + batch.getBatchCode() +
                        ". Available: " + batch.getQuantity() + ", Required: " + item.getQuantitySent());
            }

            // Update batch quantities
            batch.setQuantity(batch.getQuantity().subtract(item.getQuantitySent()));
            batch.setTransitQuantity(batch.getTransitQuantity().add(item.getQuantitySent()));
            batchRepository.save(batch);

            // Create ledger entry
            InventoryLedger ledgerEntry = InventoryLedger.builder()
                    .tenantId(tenantId)
                    .warehouseId(transfer.getSourceWarehouseId())
                    .batchId(batch.getId())
                    .productId(item.getProductId())
                    .entryType(LedgerEntryType.TRANSFER_OUT)
                    .quantity(item.getQuantitySent().negate())
                    .referenceType("TRANSFER")
                    .referenceId(transfer.getId())
                    .notes("Transfer to " + destinationWarehouse.getName())
                    .createdBy(userId)
                    .build();
            ledgerRepository.save(ledgerEntry);
        }

        // Update transfer status
        transfer.setStatus(TransferStatus.IN_TRANSIT);
        transfer.setExecutedByUserId(userId);
        transfer.setExecutedAt(Instant.now());

        Transfer saved = transferRepository.save(transfer);
        log.info("Transfer {} executed by user {}", saved.getCode(), userId);

        String sourceWarehouseName = warehouseRepository.findById(transfer.getSourceWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");

        return transferMapper.toResponse(saved, sourceWarehouseName, destinationWarehouse.getName());
    }

    @Transactional
    public TransferResponse cancel(UUID id, CancelTransferRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentWarehouseId = securityUtils.getCurrentWarehouseId();
        UUID userId = securityUtils.getCurrentUserId();

        Transfer transfer = transferRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        validateSourceWarehouseAccess(transfer, currentWarehouseId);
        stateMachine.validateTransition(transfer.getStatus(), TransferStatus.CANCELLED);

        if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            if (request.getReason() == null || request.getReason().isBlank()) {
                throw new BadRequestException("Cancellation reason is required for in-transit transfers");
            }

            // Revert stock movements
            for (TransferItem item : transfer.getItems()) {
                Batch batch = batchRepository.findByIdForUpdate(item.getSourceBatchId())
                        .orElseThrow(() -> new ResourceNotFoundException("Batch not found"));

                batch.setTransitQuantity(batch.getTransitQuantity().subtract(item.getQuantitySent()));
                batch.setQuantity(batch.getQuantity().add(item.getQuantitySent()));
                batchRepository.save(batch);

                // Create reversal ledger entry
                InventoryLedger ledgerEntry = InventoryLedger.builder()
                        .tenantId(tenantId)
                        .warehouseId(transfer.getSourceWarehouseId())
                        .batchId(batch.getId())
                        .productId(item.getProductId())
                        .entryType(LedgerEntryType.TRANSFER_CANCELLED)
                        .quantity(item.getQuantitySent())
                        .referenceType("TRANSFER")
                        .referenceId(transfer.getId())
                        .notes("Transfer cancelled: " + request.getReason())
                        .createdBy(userId)
                        .build();
                ledgerRepository.save(ledgerEntry);
            }
        }

        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setCancelledByUserId(userId);
        transfer.setCancelledAt(Instant.now());
        transfer.setCancellationReason(request.getReason());

        Transfer saved = transferRepository.save(transfer);
        log.info("Transfer {} cancelled by user {}", saved.getCode(), userId);

        String sourceWarehouseName = warehouseRepository.findById(transfer.getSourceWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");
        String destinationWarehouseName = warehouseRepository.findById(transfer.getDestinationWarehouseId())
                .map(Warehouse::getName).orElse("Unknown");

        return transferMapper.toResponse(saved, sourceWarehouseName, destinationWarehouseName);
    }

    private void validateSourceWarehouseAccess(Transfer transfer, UUID currentWarehouseId) {
        if (!transfer.getSourceWarehouseId().equals(currentWarehouseId)) {
            throw new ForbiddenException("Only source warehouse can perform this action");
        }
    }

    private void validateDestinationWarehouseAccess(Transfer transfer, UUID currentWarehouseId) {
        if (!transfer.getDestinationWarehouseId().equals(currentWarehouseId)) {
            throw new ForbiddenException("Only destination warehouse can perform this action");
        }
    }
}
