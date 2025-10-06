package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.stock.CreateStockEventLineRequest;
import com.stockshift.backend.api.dto.stock.CreateStockEventRequest;
import com.stockshift.backend.api.dto.transfer.CreateTransferLineRequest;
import com.stockshift.backend.api.dto.transfer.CreateTransferRequest;
import com.stockshift.backend.domain.product.ProductVariant;
import com.stockshift.backend.domain.stock.exception.SameWarehouseTransferException;
import com.stockshift.backend.domain.stock.StockEvent;
import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockReasonCode;
import com.stockshift.backend.domain.stock.StockTransfer;
import com.stockshift.backend.domain.stock.StockTransferLine;
import com.stockshift.backend.domain.stock.TransferStatus;
import com.stockshift.backend.domain.stock.exception.StockForbiddenException;
import com.stockshift.backend.domain.stock.exception.StockInvalidPayloadException;
import com.stockshift.backend.domain.stock.exception.StockVariantInactiveException;
import com.stockshift.backend.domain.stock.exception.StockVariantNotFoundException;
import com.stockshift.backend.domain.stock.exception.StockWarehouseInactiveException;
import com.stockshift.backend.domain.stock.exception.StockWarehouseNotFoundException;
import com.stockshift.backend.domain.stock.exception.TransferIdempotencyConflictException;
import com.stockshift.backend.domain.stock.exception.TransferNotFoundException;
import com.stockshift.backend.domain.stock.exception.TransferNotDraftException;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
import com.stockshift.backend.domain.warehouse.Warehouse;
import com.stockshift.backend.infrastructure.repository.ProductVariantRepository;
import com.stockshift.backend.infrastructure.repository.StockTransferRepository;
import com.stockshift.backend.infrastructure.repository.UserRepository;
import com.stockshift.backend.infrastructure.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository transferRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final StockEventService stockEventService;

    @Transactional
    public StockTransfer createDraft(CreateTransferRequest request, User currentUser) {
        if (currentUser == null || currentUser.getRole() == null) {
            throw new StockForbiddenException();
        }

        // Validate origin != destination
        if (request.originWarehouseId().equals(request.destinationWarehouseId())) {
            throw new SameWarehouseTransferException();
        }

        // Fetch and validate warehouses
        Warehouse originWarehouse = warehouseRepository.findById(request.originWarehouseId())
                .orElseThrow(StockWarehouseNotFoundException::new);

        Warehouse destinationWarehouse = warehouseRepository.findById(request.destinationWarehouseId())
                .orElseThrow(StockWarehouseNotFoundException::new);

        if (!Boolean.TRUE.equals(originWarehouse.getActive())) {
            throw new StockWarehouseInactiveException();
        }

        if (!Boolean.TRUE.equals(destinationWarehouse.getActive())) {
            throw new StockWarehouseInactiveException();
        }

        // Validate authorization
        validateCreateAccess(currentUser, originWarehouse, destinationWarehouse);

        // Validate lines
        validateLines(request.lines());

        // Fetch and validate variants
        Set<UUID> variantIds = new HashSet<>();
        for (CreateTransferLineRequest line : request.lines()) {
            if (!variantIds.add(line.variantId())) {
                throw new StockInvalidPayloadException("duplicate-variant-line");
            }
        }

        OffsetDateTime occurredAt = Optional.ofNullable(request.occurredAt())
                .map(dt -> dt.withOffsetSameInstant(ZoneOffset.UTC))
                .orElse(OffsetDateTime.now(ZoneOffset.UTC));

        // Create transfer entity
        StockTransfer transfer = new StockTransfer();
        transfer.setOriginWarehouse(originWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);
        transfer.setStatus(TransferStatus.DRAFT);
        transfer.setOccurredAt(occurredAt);
        transfer.setNotes(request.notes());

        User managedUser = userRepository.findById(currentUser.getId())
                .orElseThrow(StockForbiddenException::new);
        transfer.setCreatedBy(managedUser);
        transfer.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        // Add lines
        for (CreateTransferLineRequest lineRequest : request.lines()) {
            ProductVariant variant = variantRepository.findById(lineRequest.variantId())
                    .orElseThrow(StockVariantNotFoundException::new);

            if (!Boolean.TRUE.equals(variant.getActive())) {
                throw new StockVariantInactiveException();
            }

            if (variant.getProduct() == null || !Boolean.TRUE.equals(variant.getProduct().getActive())) {
                throw new StockInvalidPayloadException("product-inactive");
            }

            StockTransferLine line = new StockTransferLine();
            line.setVariant(variant);
            line.setQuantity(lineRequest.quantity());
            transfer.addLine(line);
        }

        return transferRepository.save(transfer);
    }

    @Transactional
    public StockTransfer confirmTransfer(UUID transferId, String idempotencyKey, User currentUser) {
        if (currentUser == null || currentUser.getRole() == null) {
            throw new StockForbiddenException();
        }

        // Check idempotency first (before any other validation)
        String normalizedKey = StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null;
        if (normalizedKey != null) {
            Optional<StockTransfer> existingWithKey = transferRepository.findByIdempotencyKey(normalizedKey);
            if (existingWithKey.isPresent()) {
                StockTransfer existing = existingWithKey.get();
                if (!existing.getId().equals(transferId)) {
                    throw new TransferIdempotencyConflictException();
                }
                // Same transfer, return it (idempotent)
                return existing;
            }
        }

        // Fetch transfer with all details
        StockTransfer transfer = transferRepository.findWithDetailsById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        // Validate status
        if (transfer.getStatus() != TransferStatus.DRAFT) {
            throw new TransferNotDraftException();
        }

        // Validate authorization
        validateConfirmAccess(currentUser, transfer.getOriginWarehouse(), transfer.getDestinationWarehouse());

        // Initialize lines to avoid lazy loading issues
        transfer.getLines().size();

        // Create OUTBOUND event at origin
        List<CreateStockEventLineRequest> outboundLines = new ArrayList<>();
        for (StockTransferLine line : transfer.getLines()) {
            outboundLines.add(new CreateStockEventLineRequest(
                    line.getVariant().getId(),
                    line.getQuantity()
            ));
        }

        CreateStockEventRequest outboundRequest = new CreateStockEventRequest(
                StockEventType.OUTBOUND,
                transfer.getOriginWarehouse().getId(),
                transfer.getOccurredAt(),
                StockReasonCode.OTHER,
                "Transfer #" + transfer.getId() + " to " + transfer.getDestinationWarehouse().getCode(),
                outboundLines
        );

        StockEvent outboundEvent = stockEventService.createStockEvent(outboundRequest, null, currentUser);

        // Create INBOUND event at destination
        List<CreateStockEventLineRequest> inboundLines = new ArrayList<>();
        for (StockTransferLine line : transfer.getLines()) {
            inboundLines.add(new CreateStockEventLineRequest(
                    line.getVariant().getId(),
                    line.getQuantity()
            ));
        }

        CreateStockEventRequest inboundRequest = new CreateStockEventRequest(
                StockEventType.INBOUND,
                transfer.getDestinationWarehouse().getId(),
                transfer.getOccurredAt(),
                StockReasonCode.OTHER,
                "Transfer #" + transfer.getId() + " from " + transfer.getOriginWarehouse().getCode(),
                inboundLines
        );

        StockEvent inboundEvent = stockEventService.createStockEvent(inboundRequest, null, currentUser);

        // Update transfer
        transfer.setStatus(TransferStatus.CONFIRMED);
        User managedConfirmer = userRepository.findById(currentUser.getId())
                .orElseThrow(StockForbiddenException::new);
        transfer.setConfirmedBy(managedConfirmer);
        transfer.setConfirmedAt(OffsetDateTime.now(ZoneOffset.UTC));
        transfer.setOutboundEvent(outboundEvent);
        transfer.setInboundEvent(inboundEvent);
        transfer.setIdempotencyKey(normalizedKey);

        return transferRepository.save(transfer);
    }

    @Transactional
    public StockTransfer cancelDraft(UUID transferId, User currentUser) {
        if (currentUser == null || currentUser.getRole() == null) {
            throw new StockForbiddenException();
        }

        StockTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        if (transfer.getStatus() != TransferStatus.DRAFT) {
            throw new TransferNotDraftException();
        }

        validateCreateAccess(currentUser, transfer.getOriginWarehouse(), transfer.getDestinationWarehouse());

        transfer.setStatus(TransferStatus.CANCELED);
        return transferRepository.save(transfer);
    }

    @Transactional(readOnly = true)
    public StockTransfer getTransfer(UUID transferId, User currentUser) {
        if (currentUser == null) {
            throw new StockForbiddenException();
        }

        StockTransfer transfer = transferRepository.findWithDetailsById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        validateReadAccess(currentUser, transfer.getOriginWarehouse(), transfer.getDestinationWarehouse());

        // Initialize collections
        transfer.getLines().size();

        return transfer;
    }

    @Transactional(readOnly = true)
    public Page<StockTransfer> listTransfers(
            TransferStatus status,
            UUID originWarehouseId,
            UUID destinationWarehouseId,
            OffsetDateTime occurredFrom,
            OffsetDateTime occurredTo,
            Pageable pageable,
            User currentUser
    ) {
        if (currentUser == null) {
            throw new StockForbiddenException();
        }

        // For SELLER, they must provide at least one warehouse filter
        if (currentUser.getRole() == UserRole.SELLER) {
            if (originWarehouseId == null && destinationWarehouseId == null) {
                throw new StockForbiddenException();
            }
        }

        OffsetDateTime normalizedFrom = occurredFrom != null ? occurredFrom.withOffsetSameInstant(ZoneOffset.UTC) : null;
        OffsetDateTime normalizedTo = occurredTo != null ? occurredTo.withOffsetSameInstant(ZoneOffset.UTC) : null;

        Page<StockTransfer> transfers = transferRepository.findByFilters(
                status,
                originWarehouseId,
                destinationWarehouseId,
                normalizedFrom,
                normalizedTo,
                pageable
        );

        // Validate access for each transfer and initialize collections
        transfers.forEach(transfer -> {
            validateReadAccess(currentUser, transfer.getOriginWarehouse(), transfer.getDestinationWarehouse());
            transfer.getLines().size();
        });

        return transfers;
    }

    private void validateLines(List<CreateTransferLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new StockInvalidPayloadException("empty-lines");
        }

        for (CreateTransferLineRequest line : lines) {
            if (line.quantity() == null || line.quantity() <= 0) {
                throw new StockInvalidPayloadException("quantity-must-be-positive");
            }
        }
    }

    private void validateCreateAccess(User user, Warehouse origin, Warehouse destination) {
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.MANAGER) {
            return;
        }

        // SELLER: may be allowed depending on policy (restrict for now)
        throw new StockForbiddenException();
    }

    private void validateConfirmAccess(User user, Warehouse origin, Warehouse destination) {
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.MANAGER) {
            return;
        }

        // SELLER cannot confirm transfers
        throw new StockForbiddenException();
    }

    private void validateReadAccess(User user, Warehouse origin, Warehouse destination) {
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.MANAGER) {
            return;
        }

        // SELLER can read (basic access)
        if (user.getRole() == UserRole.SELLER) {
            return;
        }

        throw new StockForbiddenException();
    }
}
