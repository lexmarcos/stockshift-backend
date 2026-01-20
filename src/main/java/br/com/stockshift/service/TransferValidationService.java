package br.com.stockshift.service;

import br.com.stockshift.dto.validation.*;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.model.enums.MovementType;
import br.com.stockshift.model.enums.ValidationStatus;
import br.com.stockshift.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferValidationService {

    private final TransferValidationRepository validationRepository;
    private final TransferValidationItemRepository validationItemRepository;
    private final TransferDiscrepancyRepository discrepancyRepository;
    private final StockMovementRepository movementRepository;
    private final BatchRepository batchRepository;
    private final UserRepository userRepository;

    @Transactional
    public StartValidationResponse startValidation(UUID movementId) {
        StockMovement movement = movementRepository.findById(movementId)
                .orElseThrow(() -> new ResourceNotFoundException("StockMovement", "id", movementId));

        if (movement.getMovementType() != MovementType.TRANSFER) {
            throw new BusinessException("Only TRANSFER movements can be validated");
        }

        if (movement.getStatus() != MovementStatus.IN_TRANSIT) {
            throw new BusinessException("Only IN_TRANSIT movements can be validated");
        }

        List<ValidationStatus> blockingStatuses = List.of(ValidationStatus.IN_PROGRESS, ValidationStatus.COMPLETED);
        if (validationRepository.existsByStockMovementIdAndStatusIn(movementId, blockingStatuses)) {
            throw new BusinessException("A validation already exists for this movement");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        TransferValidation validation = new TransferValidation();
        validation.setStockMovement(movement);
        validation.setValidatedBy(user);
        validation.setStatus(ValidationStatus.IN_PROGRESS);
        validation.setStartedAt(LocalDateTime.now());

        List<TransferValidationItem> validationItems = new ArrayList<>();
        for (StockMovementItem movementItem : movement.getItems()) {
            TransferValidationItem validationItem = new TransferValidationItem();
            validationItem.setTransferValidation(validation);
            validationItem.setStockMovementItem(movementItem);
            validationItem.setExpectedQuantity(movementItem.getQuantity());
            validationItem.setReceivedQuantity(0);
            validationItems.add(validationItem);
        }
        validation.setItems(validationItems);

        TransferValidation saved = validationRepository.save(validation);
        log.info("Started validation {} for movement {}", saved.getId(), movementId);

        return StartValidationResponse.builder()
                .validationId(saved.getId())
                .startedAt(saved.getStartedAt())
                .items(mapItemsToResponse(saved.getItems()))
                .build();
    }

    @Transactional
    public ScanResponse scanBarcode(UUID movementId, UUID validationId, String barcode) {
        TransferValidation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("TransferValidation", "id", validationId));

        if (!validation.getStockMovement().getId().equals(movementId)) {
            throw new BusinessException("Validation does not belong to this movement");
        }

        if (validation.getStatus() != ValidationStatus.IN_PROGRESS) {
            throw new BusinessException("Cannot scan items on a completed validation");
        }

        TransferValidationItem item = validationItemRepository
                .findByValidationIdAndProductBarcode(validationId, barcode)
                .orElse(null);

        if (item == null) {
            log.warn("Scan attempt with unknown barcode {} for validation {}", barcode, validationId);
            return ScanResponse.builder()
                    .success(false)
                    .message("Produto não faz parte desta transferência")
                    .barcode(barcode)
                    .build();
        }

        item.setReceivedQuantity(item.getReceivedQuantity() + 1);
        item.setScannedAt(LocalDateTime.now());
        validationItemRepository.save(item);

        log.info("Scanned barcode {} for validation {}, new quantity: {}/{}",
                barcode, validationId, item.getReceivedQuantity(), item.getExpectedQuantity());

        return ScanResponse.builder()
                .success(true)
                .message("Produto escaneado com sucesso")
                .barcode(barcode)
                .item(mapItemToResponse(item))
                .build();
    }

    @Transactional(readOnly = true)
    public ValidationProgressResponse getProgress(UUID movementId, UUID validationId) {
        TransferValidation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("TransferValidation", "id", validationId));

        if (!validation.getStockMovement().getId().equals(movementId)) {
            throw new BusinessException("Validation does not belong to this movement");
        }

        List<ValidationItemResponse> items = mapItemsToResponse(validation.getItems());

        int complete = 0, partial = 0, pending = 0;
        for (ValidationItemResponse item : items) {
            switch (item.getStatus()) {
                case "COMPLETE" -> complete++;
                case "PARTIAL" -> partial++;
                case "PENDING" -> pending++;
            }
        }

        return ValidationProgressResponse.builder()
                .validationId(validation.getId())
                .status(validation.getStatus().name())
                .startedAt(validation.getStartedAt())
                .items(items)
                .progress(ValidationProgressResponse.ProgressSummary.builder()
                        .totalItems(items.size())
                        .completeItems(complete)
                        .partialItems(partial)
                        .pendingItems(pending)
                        .build())
                .build();
    }

    @Transactional
    public CompleteValidationResponse completeValidation(UUID movementId, UUID validationId) {
        TransferValidation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("TransferValidation", "id", validationId));

        if (!validation.getStockMovement().getId().equals(movementId)) {
            throw new BusinessException("Validation does not belong to this movement");
        }

        if (validation.getStatus() != ValidationStatus.IN_PROGRESS) {
            throw new BusinessException("Validation is already completed");
        }

        StockMovement movement = validation.getStockMovement();
        List<TransferDiscrepancy> discrepancies = new ArrayList<>();
        int totalExpected = 0;
        int totalReceived = 0;

        for (TransferValidationItem item : validation.getItems()) {
            totalExpected += item.getExpectedQuantity();
            totalReceived += item.getReceivedQuantity();

            if (item.getReceivedQuantity() > 0) {
                addStockToDestination(movement, item.getStockMovementItem(), item.getReceivedQuantity());
            }

            if (item.getReceivedQuantity() < item.getExpectedQuantity()) {
                TransferDiscrepancy discrepancy = new TransferDiscrepancy();
                discrepancy.setTransferValidation(validation);
                discrepancy.setStockMovementItem(item.getStockMovementItem());
                discrepancy.setExpectedQuantity(item.getExpectedQuantity());
                discrepancy.setReceivedQuantity(item.getReceivedQuantity());
                discrepancy.setMissingQuantity(item.getExpectedQuantity() - item.getReceivedQuantity());
                discrepancies.add(discrepancy);
            }
        }

        if (!discrepancies.isEmpty()) {
            discrepancyRepository.saveAll(discrepancies);
        }

        validation.setStatus(ValidationStatus.COMPLETED);
        validation.setCompletedAt(LocalDateTime.now());
        validationRepository.save(validation);

        MovementStatus newStatus = discrepancies.isEmpty()
                ? MovementStatus.COMPLETED
                : MovementStatus.COMPLETED_WITH_DISCREPANCY;
        movement.setStatus(newStatus);
        movement.setCompletedAt(LocalDateTime.now());
        movementRepository.save(movement);

        log.info("Completed validation {} for movement {}. Status: {}, Discrepancies: {}",
                validationId, movementId, newStatus, discrepancies.size());

        List<DiscrepancyResponse> discrepancyResponses = discrepancies.stream()
                .map(d -> DiscrepancyResponse.builder()
                        .productId(d.getStockMovementItem().getProduct().getId())
                        .productName(d.getStockMovementItem().getProduct().getName())
                        .expected(d.getExpectedQuantity())
                        .received(d.getReceivedQuantity())
                        .missing(d.getMissingQuantity())
                        .build())
                .collect(Collectors.toList());

        String reportUrl = discrepancies.isEmpty() ? null
                : String.format("/api/stock-movements/%s/validations/%s/discrepancy-report", movementId, validationId);

        return CompleteValidationResponse.builder()
                .validationId(validation.getId())
                .status(newStatus.name())
                .completedAt(validation.getCompletedAt())
                .summary(CompleteValidationResponse.ValidationSummary.builder()
                        .totalExpected(totalExpected)
                        .totalReceived(totalReceived)
                        .totalMissing(totalExpected - totalReceived)
                        .build())
                .discrepancies(discrepancyResponses)
                .reportUrl(reportUrl)
                .build();
    }

    private void addStockToDestination(StockMovement movement, StockMovementItem item, int quantity) {
        Batch sourceBatch = item.getBatch();
        Warehouse destWarehouse = movement.getDestinationWarehouse();

        List<Batch> destBatches = batchRepository.findByProductIdAndWarehouseIdAndTenantId(
                item.getProduct().getId(),
                destWarehouse.getId(),
                movement.getTenantId());

        Batch destBatch;
        if (!destBatches.isEmpty()) {
            destBatch = destBatches.get(0);
            destBatch.setQuantity(destBatch.getQuantity() + quantity);
        } else {
            destBatch = new Batch();
            destBatch.setTenantId(movement.getTenantId());
            destBatch.setProduct(item.getProduct());
            destBatch.setWarehouse(destWarehouse);
            destBatch.setBatchCode(sourceBatch.getBatchCode() + "-TRANSFER");
            destBatch.setQuantity(quantity);
            destBatch.setManufacturedDate(sourceBatch.getManufacturedDate());
            destBatch.setExpirationDate(sourceBatch.getExpirationDate());
            destBatch.setCostPrice(sourceBatch.getCostPrice());
            destBatch.setSellingPrice(sourceBatch.getSellingPrice());
        }
        batchRepository.save(destBatch);
    }

    private List<ValidationItemResponse> mapItemsToResponse(List<TransferValidationItem> items) {
        return items.stream()
                .map(this::mapItemToResponse)
                .collect(Collectors.toList());
    }

    private ValidationItemResponse mapItemToResponse(TransferValidationItem item) {
        Product product = item.getStockMovementItem().getProduct();
        String status;
        if (item.getReceivedQuantity() == 0) {
            status = "PENDING";
        } else if (item.getReceivedQuantity() >= item.getExpectedQuantity()) {
            status = "COMPLETE";
        } else {
            status = "PARTIAL";
        }

        return ValidationItemResponse.builder()
                .itemId(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .barcode(product.getBarcode())
                .expectedQuantity(item.getExpectedQuantity())
                .scannedQuantity(item.getReceivedQuantity())
                .status(status)
                .build();
    }
}
