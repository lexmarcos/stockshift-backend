package br.com.stockshift.service.transfer;

import br.com.stockshift.dto.transfer.CreateTransferRequest;
import br.com.stockshift.dto.transfer.TransferItemRequest;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.exception.InsufficientStockException;
import br.com.stockshift.exception.InvalidTransferStateException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.*;
import br.com.stockshift.repository.*;
import br.com.stockshift.service.WarehouseAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TransferRepository transferRepository;
    private final TransferSecurityService securityService;
    private final TransferStateMachine stateMachine;
    private final WarehouseAccessService warehouseAccessService;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final BatchRepository batchRepository;
    private final InventoryLedgerRepository inventoryLedgerRepository;
    private final TransferInTransitRepository transferInTransitRepository;
    private final DiscrepancyService discrepancyService;

    @Transactional
    public Transfer createTransfer(CreateTransferRequest request, User user) {
        log.info("Creating transfer from {} to {} by user {}",
            request.getSourceWarehouseId(), request.getDestinationWarehouseId(), user.getId());

        // Validate source != destination
        if (request.getSourceWarehouseId().equals(request.getDestinationWarehouseId())) {
            throw new BusinessException("Source and destination warehouses must be different");
        }

        // Validate source warehouse access
        securityService.validateSourceWarehouseAccess(request.getSourceWarehouseId());

        // Load warehouses
        Warehouse sourceWarehouse = warehouseRepository.findById(request.getSourceWarehouseId())
            .orElseThrow(() -> new ResourceNotFoundException("Source warehouse not found"));
        Warehouse destinationWarehouse = warehouseRepository.findById(request.getDestinationWarehouseId())
            .orElseThrow(() -> new ResourceNotFoundException("Destination warehouse not found"));

        // Create transfer
        Transfer transfer = new Transfer();
        transfer.setTenantId(warehouseAccessService.getTenantId());
        transfer.setTransferCode(generateTransferCode());
        transfer.setStatus(TransferStatus.DRAFT);
        transfer.setSourceWarehouse(sourceWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);
        transfer.setCreatedBy(user);
        transfer.setCreatedAt(LocalDateTime.now());
        transfer.setNotes(request.getNotes());

        // Add items
        for (TransferItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.getProductId()));
            Batch batch = batchRepository.findById(itemRequest.getBatchId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + itemRequest.getBatchId()));

            TransferItem item = new TransferItem();
            item.setTenantId(transfer.getTenantId());
            item.setProduct(product);
            item.setSourceBatch(batch);
            item.setExpectedQuantity(itemRequest.getQuantity());
            item.setStatus(TransferItemStatus.PENDING);
            transfer.addItem(item);
        }

        transfer = transferRepository.save(transfer);
        log.info("Transfer {} created successfully", transfer.getTransferCode());

        return transfer;
    }

    private String generateTransferCode() {
        String year = String.valueOf(LocalDateTime.now().getYear());
        long count = transferRepository.count() + 1;
        return String.format("TRF-%s-%05d", year, count);
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(UUID id) {
        Transfer transfer = transferRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));

        UUID currentTenantId = warehouseAccessService.getTenantId();
        if (!transfer.getTenantId().equals(currentTenantId)) {
            throw new ForbiddenException("Transfer not found");
        }

        return transfer;
    }

    @Transactional
    public Transfer updateTransfer(UUID id, br.com.stockshift.dto.transfer.UpdateTransferRequest request, User user) {
        log.info("Updating transfer {} by user {}", id, user.getId());

        Transfer transfer = getTransferForUpdate(id);

        if (transfer.getStatus() != TransferStatus.DRAFT) {
            throw new InvalidTransferStateException("Only DRAFT transfers can be updated");
        }

        securityService.validateAction(transfer, TransferAction.UPDATE);

        transfer.setNotes(request.getNotes());

        // Update items if provided
        if (request.getItems() != null) {
            // Remove existing items
            transfer.getItems().clear();

            // Add new items
            for (br.com.stockshift.dto.transfer.TransferItemRequest itemRequest : request.getItems()) {
                Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.getProductId()));
                Batch batch = batchRepository.findById(itemRequest.getBatchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + itemRequest.getBatchId()));

                TransferItem item = new TransferItem();
                item.setTenantId(transfer.getTenantId());
                item.setProduct(product);
                item.setSourceBatch(batch);
                item.setExpectedQuantity(itemRequest.getQuantity());
                item.setStatus(TransferItemStatus.PENDING);
                transfer.addItem(item);
            }
        }

        return transferRepository.save(transfer);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Transfer> listTransfers(UUID warehouseId, String status, String direction, org.springframework.data.domain.Pageable pageable, User user) {
        UUID tenantId = warehouseAccessService.getTenantId();

        // Simple implementation for Phase 3, can be expanded with Specification in Phase 4
        if (warehouseId != null) {
            return transferRepository.findAllByTenantId(tenantId, pageable); // Simplified
        }

        return transferRepository.findAllByTenantId(tenantId, pageable);
    }

    public List<TransferAction> calculateAllowedActions(Transfer transfer, TransferRole role) {
        List<TransferAction> actions = new ArrayList<>();
        TransferStatus status = transfer.getStatus();

        if (role == TransferRole.OUTBOUND) {
            if (status == TransferStatus.DRAFT) {
                actions.add(TransferAction.UPDATE);
                actions.add(TransferAction.CANCEL);
                actions.add(TransferAction.DISPATCH);
            }
        } else if (role == TransferRole.INBOUND) {
            if (status == TransferStatus.IN_TRANSIT) {
                actions.add(TransferAction.START_VALIDATION);
            } else if (status == TransferStatus.VALIDATION_IN_PROGRESS) {
                actions.add(TransferAction.SCAN_ITEM);
                actions.add(TransferAction.COMPLETE);
            }
        }

        return actions;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Transfer dispatch(UUID transferId, User user) {
        log.info("Dispatching transfer {} by user {}", transferId, user.getId());

        Transfer transfer = getTransferForUpdate(transferId);

        // Idempotency: if already dispatched, return success
        if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            log.info("Transfer {} already dispatched, returning existing state", transferId);
            return transfer;
        }

        // Validate action
        securityService.validateAction(transfer, TransferAction.DISPATCH);

        // Lock batches in order to avoid deadlocks
        List<UUID> batchIds = transfer.getItems().stream()
            .map(item -> item.getSourceBatch().getId())
            .sorted()
            .collect(Collectors.toList());

        Map<UUID, Batch> batches = new HashMap<>();
        for (UUID batchId : batchIds) {
            Batch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + batchId));
            batches.put(batchId, batch);
        }

        // Process each item
        for (TransferItem item : transfer.getItems()) {
            Batch batch = batches.get(item.getSourceBatch().getId());

            // Validate stock
            if (batch.getQuantity().compareTo(item.getExpectedQuantity()) < 0) {
                throw new InsufficientStockException(
                    String.format("Insufficient stock in batch %s. Available: %s, Required: %s",
                        batch.getBatchCode(), batch.getQuantity(), item.getExpectedQuantity())
                );
            }

            // Create TRANSFER_OUT ledger entry
            InventoryLedger outEntry = new InventoryLedger();
            outEntry.setTenantId(transfer.getTenantId());
            outEntry.setWarehouseId(transfer.getSourceWarehouse().getId());
            outEntry.setProductId(item.getProduct().getId());
            outEntry.setBatchId(batch.getId());
            outEntry.setEntryType(LedgerEntryType.TRANSFER_OUT);
            outEntry.setQuantity(item.getExpectedQuantity());
            outEntry.setBalanceAfter(batch.getQuantity().subtract(item.getExpectedQuantity()));
            outEntry.setReferenceType("TRANSFER");
            outEntry.setReferenceId(transfer.getId());
            outEntry.setTransferItemId(item.getId());
            outEntry.setCreatedBy(user.getId());
            inventoryLedgerRepository.save(outEntry);

            // Create TRANSFER_IN_TRANSIT ledger entry (virtual)
            InventoryLedger transitEntry = new InventoryLedger();
            transitEntry.setTenantId(transfer.getTenantId());
            transitEntry.setWarehouseId(null); // Virtual
            transitEntry.setProductId(item.getProduct().getId());
            transitEntry.setBatchId(null);
            transitEntry.setEntryType(LedgerEntryType.TRANSFER_IN_TRANSIT);
            transitEntry.setQuantity(item.getExpectedQuantity());
            transitEntry.setBalanceAfter(null);
            transitEntry.setReferenceType("TRANSFER");
            transitEntry.setReferenceId(transfer.getId());
            transitEntry.setTransferItemId(item.getId());
            transitEntry.setCreatedBy(user.getId());
            inventoryLedgerRepository.save(transitEntry);

            // Update batch quantity
            batch.setQuantity(batch.getQuantity().subtract(item.getExpectedQuantity()));
            batchRepository.save(batch);

            // Create TransferInTransit record
            TransferInTransit inTransit = new TransferInTransit();
            inTransit.setTenantId(transfer.getTenantId());
            inTransit.setTransfer(transfer);
            inTransit.setTransferItem(item);
            inTransit.setProduct(item.getProduct());
            inTransit.setSourceBatch(batch);
            inTransit.setQuantity(item.getExpectedQuantity());
            transferInTransitRepository.save(inTransit);
        }

        // Execute state transition
        TransferRole role = securityService.determineUserRole(transfer);
        TransferStatus newStatus = stateMachine.transition(transfer.getStatus(), TransferAction.DISPATCH, role);

        // Update transfer
        transfer.setStatus(newStatus);
        transfer.setDispatchedBy(user);
        transfer.setDispatchedAt(LocalDateTime.now());

        transfer = transferRepository.save(transfer);
        log.info("Transfer {} dispatched successfully", transferId);

        return transfer;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Transfer startValidation(UUID transferId, User user) {
        log.info("Starting validation for transfer {} by user {}", transferId, user.getId());

        Transfer transfer = getTransferForUpdate(transferId);

        // Idempotency: if already in validation, return success
        if (transfer.getStatus() == TransferStatus.VALIDATION_IN_PROGRESS) {
            log.info("Transfer {} validation already started, returning existing state", transferId);
            return transfer;
        }

        // Validate action
        securityService.validateAction(transfer, TransferAction.START_VALIDATION);

        // Execute state transition
        TransferRole role = securityService.determineUserRole(transfer);
        TransferStatus newStatus = stateMachine.transition(transfer.getStatus(), TransferAction.START_VALIDATION, role);

        // Update transfer
        transfer.setStatus(newStatus);
        transfer.setValidationStartedBy(user);
        transfer.setValidationStartedAt(LocalDateTime.now());

        transfer = transferRepository.save(transfer);
        log.info("Transfer {} validation started successfully", transferId);

        return transfer;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Transfer cancel(UUID transferId, String reason, User user) {
        log.info("Cancelling transfer {} by user {}", transferId, user.getId());

        Transfer transfer = getTransferForUpdate(transferId);

        // Idempotency: if already cancelled, return success
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            log.info("Transfer {} already cancelled, returning existing state", transferId);
            return transfer;
        }

        // Validate action
        securityService.validateAction(transfer, TransferAction.CANCEL);

        // Execute state transition
        TransferRole role = securityService.determineUserRole(transfer);
        TransferStatus newStatus = stateMachine.transition(transfer.getStatus(), TransferAction.CANCEL, role);

        // Update transfer
        transfer.setStatus(newStatus);
        transfer.setCancelledBy(user);
        transfer.setCancelledAt(LocalDateTime.now());
        transfer.setCancellationReason(reason);

        transfer = transferRepository.save(transfer);
        log.info("Transfer {} cancelled successfully", transferId);

        return transfer;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Transfer scanItem(UUID transferId, br.com.stockshift.dto.transfer.ScanItemRequest request, User user) {
        log.info("Scanning item {} for transfer {} by user {}", request.getBarcode(), transferId, user.getId());

        Transfer transfer = getTransferForUpdate(transferId);

        // Validate status
        if (transfer.getStatus() != TransferStatus.VALIDATION_IN_PROGRESS) {
            throw new InvalidTransferStateException(
                "Cannot scan items for transfer in status " + transfer.getStatus()
            );
        }

        // Validate action
        securityService.validateAction(transfer, TransferAction.SCAN_ITEM);

        // Find item by barcode
        TransferItem item = transfer.getItems().stream()
            .filter(i -> i.getProduct().getBarcode() != null &&
                         i.getProduct().getBarcode().equals(request.getBarcode()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(
                "No item found with barcode: " + request.getBarcode()
            ));

        // Update received quantity
        BigDecimal currentReceived = item.getReceivedQuantity() != null
            ? item.getReceivedQuantity()
            : BigDecimal.ZERO;
        BigDecimal newReceived = currentReceived.add(request.getQuantity());
        item.setReceivedQuantity(newReceived);

        // Update item status
        if (newReceived.compareTo(item.getExpectedQuantity()) == 0) {
            item.setStatus(TransferItemStatus.RECEIVED);
        } else if (newReceived.compareTo(item.getExpectedQuantity()) < 0) {
            item.setStatus(TransferItemStatus.PARTIAL);
        } else {
            item.setStatus(TransferItemStatus.EXCESS);
        }

        transfer = transferRepository.save(transfer);
        log.info("Item scanned successfully for transfer {}", transferId);

        return transfer;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Transfer completeValidation(UUID transferId, User user) {
        log.info("Completing validation for transfer {} by user {}", transferId, user.getId());

        Transfer transfer = getTransferForUpdate(transferId);

        // Idempotency
        if (transfer.getStatus() == TransferStatus.COMPLETED ||
            transfer.getStatus() == TransferStatus.COMPLETED_WITH_DISCREPANCY) {
            log.info("Transfer {} already completed, returning existing state", transferId);
            return transfer;
        }

        // Validate status
        if (transfer.getStatus() != TransferStatus.VALIDATION_IN_PROGRESS) {
            throw new InvalidTransferStateException(
                "Cannot complete validation for transfer in status " + transfer.getStatus()
            );
        }

        // Validate action
        securityService.validateAction(transfer, TransferAction.COMPLETE);

        boolean hasDiscrepancy = false;

        // Process each item
        for (TransferItem item : transfer.getItems()) {
            BigDecimal received = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : BigDecimal.ZERO;

            if (received.compareTo(BigDecimal.ZERO) <= 0) {
                item.setStatus(TransferItemStatus.MISSING);
                hasDiscrepancy = true;
                discrepancyService.createDiscrepancy(transfer, item, DiscrepancyType.SHORTAGE);
                continue;
            }

            // Create or find destination batch (mirror)
            Batch destinationBatch = resolveDestinationBatch(transfer, item);

            // Record TRANSFER_IN
            InventoryLedger inEntry = new InventoryLedger();
            inEntry.setTenantId(transfer.getTenantId());
            inEntry.setWarehouseId(transfer.getDestinationWarehouse().getId());
            inEntry.setProductId(item.getProduct().getId());
            inEntry.setBatchId(destinationBatch.getId());
            inEntry.setEntryType(LedgerEntryType.TRANSFER_IN);
            inEntry.setQuantity(received);
            inEntry.setBalanceAfter(destinationBatch.getQuantity().add(received));
            inEntry.setReferenceType("TRANSFER");
            inEntry.setReferenceId(transfer.getId());
            inEntry.setTransferItemId(item.getId());
            inEntry.setCreatedBy(user.getId());
            inventoryLedgerRepository.save(inEntry);

            // Record TRANSFER_TRANSIT_CONSUMED
            InventoryLedger consumedEntry = new InventoryLedger();
            consumedEntry.setTenantId(transfer.getTenantId());
            consumedEntry.setWarehouseId(null);
            consumedEntry.setProductId(item.getProduct().getId());
            consumedEntry.setBatchId(null);
            consumedEntry.setEntryType(LedgerEntryType.TRANSFER_TRANSIT_CONSUMED);
            consumedEntry.setQuantity(received);
            consumedEntry.setBalanceAfter(null);
            consumedEntry.setReferenceType("TRANSFER");
            consumedEntry.setReferenceId(transfer.getId());
            consumedEntry.setTransferItemId(item.getId());
            consumedEntry.setCreatedBy(user.getId());
            inventoryLedgerRepository.save(consumedEntry);

            // Update destination batch
            destinationBatch.setQuantity(destinationBatch.getQuantity().add(received));
            batchRepository.save(destinationBatch);

            // Update TransferInTransit
            TransferInTransit inTransit = transferInTransitRepository.findByTransferItemId(item.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TransferInTransit not found for item: " + item.getId()));
            inTransit.setQuantity(inTransit.getQuantity().subtract(received));
            if (inTransit.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                inTransit.setConsumedAt(LocalDateTime.now());
            }
            transferInTransitRepository.save(inTransit);

            // Link destination batch to item
            item.setDestinationBatch(destinationBatch);

            // Evaluate discrepancy using DiscrepancyService
            DiscrepancyService.ValidationResult validationResult = discrepancyService.evaluateItem(item);
            if (validationResult.hasDiscrepancy()) {
                hasDiscrepancy = true;
                discrepancyService.createDiscrepancy(transfer, item, validationResult.discrepancyType());
            }
        }

        // Determine final status
        TransferStatus finalStatus = hasDiscrepancy
            ? TransferStatus.COMPLETED_WITH_DISCREPANCY
            : TransferStatus.COMPLETED;

        transfer.setStatus(finalStatus);
        transfer.setCompletedBy(user);
        transfer.setCompletedAt(LocalDateTime.now());

        transfer = transferRepository.save(transfer);
        log.info("Transfer {} completed with status {}", transferId, finalStatus);

        return transfer;
    }

    private Batch resolveDestinationBatch(Transfer transfer, TransferItem item) {
        Batch sourceBatch = item.getSourceBatch();
        String mirrorBatchCode = sourceBatch.getBatchCode() + "-" + transfer.getDestinationWarehouse().getCode();

        // Try to find existing compatible batch
        Optional<Batch> existing = batchRepository.findByWarehouseIdAndBatchCode(
            transfer.getDestinationWarehouse().getId(),
            mirrorBatchCode
        );

        if (existing.isPresent()) {
            return existing.get();
        }

        // Create mirror batch
        Batch newBatch = new Batch();
        newBatch.setTenantId(transfer.getTenantId());
        newBatch.setProduct(item.getProduct());
        newBatch.setWarehouse(transfer.getDestinationWarehouse());
        newBatch.setBatchCode(mirrorBatchCode);
        newBatch.setQuantity(BigDecimal.ZERO);
        newBatch.setExpirationDate(sourceBatch.getExpirationDate());
        newBatch.setManufacturedDate(sourceBatch.getManufacturedDate());
        newBatch.setOriginTransfer(transfer);
        newBatch.setOriginBatch(sourceBatch);

        return batchRepository.save(newBatch);
    }

    private Transfer getTransferForUpdate(UUID transferId) {
        Transfer transfer = transferRepository.findByIdForUpdate(transferId)
            .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + transferId));

        // Validate tenant access
        UUID currentTenantId = warehouseAccessService.getTenantId();
        if (!transfer.getTenantId().equals(currentTenantId)) {
            throw new ForbiddenException("Transfer not found");
        }

        return transfer;
    }
}
