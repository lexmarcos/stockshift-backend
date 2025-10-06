package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.stock.CreateStockEventLineRequest;
import com.stockshift.backend.api.dto.stock.CreateStockEventRequest;
import com.stockshift.backend.domain.product.ProductVariant;
import com.stockshift.backend.domain.stock.StockEvent;
import com.stockshift.backend.domain.stock.StockEventLine;
import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockItem;
import com.stockshift.backend.domain.stock.StockReasonCode;
import com.stockshift.backend.domain.stock.exception.StockConcurrencyConflictException;
import com.stockshift.backend.domain.stock.exception.StockEventNotFoundException;
import com.stockshift.backend.domain.stock.exception.StockExpiredItemMovementBlockedException;
import com.stockshift.backend.domain.stock.exception.StockForbiddenException;
import com.stockshift.backend.domain.stock.exception.StockIdempotencyConflictException;
import com.stockshift.backend.domain.stock.exception.StockInsufficientQuantityException;
import com.stockshift.backend.domain.stock.exception.StockInvalidPayloadException;
import com.stockshift.backend.domain.stock.exception.StockVariantInactiveException;
import com.stockshift.backend.domain.stock.exception.StockVariantNotFoundException;
import com.stockshift.backend.domain.stock.exception.StockWarehouseInactiveException;
import com.stockshift.backend.domain.stock.exception.StockWarehouseNotFoundException;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
import com.stockshift.backend.domain.warehouse.Warehouse;
import com.stockshift.backend.infrastructure.repository.ProductVariantRepository;
import com.stockshift.backend.infrastructure.repository.StockEventRepository;
import com.stockshift.backend.infrastructure.repository.StockItemRepository;
import com.stockshift.backend.infrastructure.repository.UserRepository;
import com.stockshift.backend.infrastructure.repository.WarehouseRepository;
import com.stockshift.backend.infrastructure.repository.specification.StockEventSpecifications;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockEventService {

    private final StockEventRepository stockEventRepository;
    private final StockItemRepository stockItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;

    @Transactional
    public StockEvent createStockEvent(
            CreateStockEventRequest request,
            String idempotencyKey,
            User currentUser
    ) {
        if (currentUser == null || currentUser.getRole() == null) {
            throw new StockForbiddenException();
        }

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(StockWarehouseNotFoundException::new);

        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new StockWarehouseInactiveException();
        }

        validateCreateAccess(currentUser, request.type(), warehouse);

        OffsetDateTime occurredAt = Optional.ofNullable(request.occurredAt())
                .map(dt -> dt.withOffsetSameInstant(ZoneOffset.UTC))
                .orElse(OffsetDateTime.now(ZoneOffset.UTC));

        String normalizedKey = StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null;
        if (normalizedKey != null) {
            Optional<StockEvent> existing = stockEventRepository.findByIdempotencyKey(normalizedKey);
            if (existing.isPresent()) {
                StockEvent existingEvent = existing.get();
                ensureIdempotencyCompatibility(request, existingEvent);
                return existingEvent;
            }
        }

        ensureLinesValid(request);

        List<CreateStockEventLineRequest> lineRequests = request.lines();
        Set<UUID> variantIds = new HashSet<>();
        for (CreateStockEventLineRequest line : lineRequests) {
            if (!variantIds.add(line.variantId())) {
                throw new StockInvalidPayloadException("duplicate-variant-line");
            }
        }

        Map<UUID, StockItem> existingStockItems = stockItemRepository
                .findByWarehouseIdAndVariantIdIn(warehouse.getId(), new ArrayList<>(variantIds))
                .stream()
                .collect(Collectors.toMap(item -> item.getVariant().getId(), item -> item));

        List<StockItem> itemsToPersist = new ArrayList<>();
        StockEvent event = new StockEvent();
        event.setWarehouse(warehouse);
        event.setType(request.type());
        event.setOccurredAt(occurredAt);
        event.setReasonCode(request.reasonCode());
        event.setNotes(request.notes());
        event.setIdempotencyKey(normalizedKey);

        User managedUser = userRepository.findById(currentUser.getId())
                .orElseThrow(StockForbiddenException::new);
        event.setCreatedBy(managedUser);

        for (CreateStockEventLineRequest lineRequest : lineRequests) {
            ProductVariant variant = productVariantRepository.findById(lineRequest.variantId())
                    .orElseThrow(StockVariantNotFoundException::new);

            if (!Boolean.TRUE.equals(variant.getActive())) {
                throw new StockVariantInactiveException();
            }

            if (variant.getProduct() == null || !Boolean.TRUE.equals(variant.getProduct().getActive())) {
                throw new StockInvalidPayloadException("product-inactive");
            }

            if (isExpired(variant, occurredAt) && !isDiscardExpired(request)) {
                throw new StockExpiredItemMovementBlockedException();
            }

            long delta = resolveQuantityDelta(request.type(), lineRequest.quantity());

            StockItem stockItem = existingStockItems.computeIfAbsent(variant.getId(), variantId -> {
                StockItem newItem = new StockItem();
                newItem.setWarehouse(warehouse);
                newItem.setVariant(variant);
                newItem.setQuantity(0L);
                return newItem;
            });

            long currentQuantity = Optional.ofNullable(stockItem.getQuantity()).orElse(0L);
            long updatedQuantity = currentQuantity + delta;
            if (updatedQuantity < 0) {
                throw new StockInsufficientQuantityException();
            }
            stockItem.setQuantity(updatedQuantity);
            if (!itemsToPersist.contains(stockItem)) {
                itemsToPersist.add(stockItem);
            }

            StockEventLine eventLine = new StockEventLine();
            eventLine.setVariant(variant);
            eventLine.setQuantity(delta);
            event.addLine(eventLine);
        }

        try {
            stockItemRepository.saveAll(itemsToPersist);
            return stockEventRepository.save(event);
        } catch (OptimisticLockException | OptimisticLockingFailureException e) {
            throw new StockConcurrencyConflictException();
        } catch (DataIntegrityViolationException e) {
            if (normalizedKey != null) {
                throw new StockIdempotencyConflictException();
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public StockEvent getStockEvent(UUID id, User currentUser) {
        if (currentUser == null) {
            throw new StockForbiddenException();
        }

        StockEvent event = stockEventRepository.findWithDetailsById(id)
                .orElseThrow(() -> new StockEventNotFoundException(id));

        validateReadAccess(currentUser, event.getWarehouse());
        event.getLines().size();
        return event;
    }

    @Transactional(readOnly = true)
    public Page<StockEvent> listStockEvents(
            StockEventType type,
            UUID warehouseId,
            UUID variantId,
            OffsetDateTime occurredFrom,
            OffsetDateTime occurredTo,
            StockReasonCode reasonCode,
            Pageable pageable,
            User currentUser
    ) {
        if (currentUser == null) {
            throw new StockForbiddenException();
        }

        if (currentUser.getRole() == UserRole.SELLER && warehouseId == null) {
            throw new StockForbiddenException();
        }

        UUID filterWarehouseId = warehouseId;
        if (currentUser.getRole() == UserRole.SELLER) {
            filterWarehouseId = warehouseId;
        }

        Specification<StockEvent> specification = Specification
                .where(StockEventSpecifications.hasType(type))
                .and(StockEventSpecifications.hasWarehouse(filterWarehouseId))
                .and(StockEventSpecifications.hasVariant(variantId))
                .and(StockEventSpecifications.occurredAfter(normalize(occurredFrom)))
                .and(StockEventSpecifications.occurredBefore(normalize(occurredTo)))
                .and(StockEventSpecifications.hasReason(reasonCode));

        Page<StockEvent> page = stockEventRepository.findAll(specification, pageable);
        page.forEach(event -> {
            validateReadAccess(currentUser, event.getWarehouse());
            event.getLines().size();
        });
        return page;
    }

    private void validateCreateAccess(User user, StockEventType type, Warehouse warehouse) {
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.MANAGER) {
            return;
        }

        if (user.getRole() == UserRole.SELLER) {
            if (type != StockEventType.OUTBOUND) {
                throw new StockForbiddenException();
            }
            return;
        }

        throw new StockForbiddenException();
    }

    private void validateReadAccess(User user, Warehouse warehouse) {
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.MANAGER) {
            return;
        }
        if (user.getRole() == UserRole.SELLER) {
            return;
        }
        throw new StockForbiddenException();
    }

    private void ensureLinesValid(CreateStockEventRequest request) {
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new StockInvalidPayloadException("empty-lines");
        }

        for (CreateStockEventLineRequest line : request.lines()) {
            if (line.quantity() == null) {
                throw new StockInvalidPayloadException("quantity-null");
            }
            if ((request.type() == StockEventType.INBOUND || request.type() == StockEventType.OUTBOUND)
                    && line.quantity() <= 0) {
                throw new StockInvalidPayloadException("quantity-must-be-positive");
            }
            if (request.type() == StockEventType.ADJUST && line.quantity() == 0) {
                throw new StockInvalidPayloadException("quantity-must-not-be-zero");
            }
        }
    }

    private long resolveQuantityDelta(StockEventType type, Long quantity) {
        if (quantity == null) {
            throw new StockInvalidPayloadException("quantity-null");
        }
        if (type == StockEventType.INBOUND) {
            if (quantity <= 0) {
                throw new StockInvalidPayloadException("quantity-must-be-positive");
            }
            return quantity;
        }
        if (type == StockEventType.OUTBOUND) {
            if (quantity <= 0) {
                throw new StockInvalidPayloadException("quantity-must-be-positive");
            }
            return -quantity;
        }
        if (quantity == 0) {
            throw new StockInvalidPayloadException("quantity-must-not-be-zero");
        }
        return quantity;
    }

    private void ensureIdempotencyCompatibility(CreateStockEventRequest request, StockEvent existingEvent) {
        if (!existingEvent.getType().equals(request.type())) {
            throw new StockIdempotencyConflictException();
        }
        if (!existingEvent.getWarehouse().getId().equals(request.warehouseId())) {
            throw new StockIdempotencyConflictException();
        }
        if (!Objects.equals(existingEvent.getReasonCode(), request.reasonCode())) {
            throw new StockIdempotencyConflictException();
        }
        if (!Objects.equals(existingEvent.getNotes(), request.notes())) {
            throw new StockIdempotencyConflictException();
        }
        if (existingEvent.getLines().size() != request.lines().size()) {
            throw new StockIdempotencyConflictException();
        }
        List<StockEventLine> sortedExisting = existingEvent.getLines().stream()
                .sorted(Comparator.comparing(line -> line.getVariant().getId()))
                .toList();
        List<CreateStockEventLineRequest> sortedRequest = request.lines().stream()
                .sorted(Comparator.comparing(CreateStockEventLineRequest::variantId))
                .toList();
        for (int i = 0; i < sortedExisting.size(); i++) {
            StockEventLine existingLine = sortedExisting.get(i);
            CreateStockEventLineRequest requestLine = sortedRequest.get(i);
            if (!existingLine.getVariant().getId().equals(requestLine.variantId())) {
                throw new StockIdempotencyConflictException();
            }
            long expectedDelta = resolveQuantityDelta(request.type(), requestLine.quantity());
            if (!existingLine.getQuantity().equals(expectedDelta)) {
                throw new StockIdempotencyConflictException();
            }
        }
    }

    private boolean isExpired(ProductVariant variant, OffsetDateTime occurredAt) {
        if (variant.getProduct() == null || variant.getProduct().getExpiryDate() == null) {
            return false;
        }
        return variant.getProduct().getExpiryDate().isBefore(occurredAt.toLocalDate());
    }

    private boolean isDiscardExpired(CreateStockEventRequest request) {
        return request.type() == StockEventType.ADJUST
                && request.reasonCode() == StockReasonCode.DISCARD_EXPIRED;
    }

    private OffsetDateTime normalize(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
