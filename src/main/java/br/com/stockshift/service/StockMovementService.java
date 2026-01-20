package br.com.stockshift.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.stockshift.dto.movement.StockMovementItemRequest;
import br.com.stockshift.dto.movement.StockMovementItemResponse;
import br.com.stockshift.dto.movement.StockMovementRequest;
import br.com.stockshift.dto.movement.StockMovementResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.entity.StockMovementItem;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.model.enums.MovementType;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.StockMovementItemRepository;
import br.com.stockshift.repository.StockMovementRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    @SuppressWarnings("unused") // Will be used for creating movement items
    private final StockMovementItemRepository stockMovementItemRepository;
    private final ProductRepository productRepository;
    private final BatchRepository batchRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    @Transactional
    public StockMovementResponse create(StockMovementRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Get current user
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        // Validate warehouses based on movement type
        validateWarehouses(request, tenantId);

        StockMovement movement = new StockMovement();
        movement.setTenantId(tenantId);
        movement.setMovementType(request.getMovementType());
        movement.setStatus(MovementStatus.PENDING);
        movement.setUser(user);
        movement.setNotes(request.getNotes());

        if (request.getSourceWarehouseId() != null) {
            Warehouse source = warehouseRepository.findByTenantIdAndId(tenantId, request.getSourceWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source warehouse", "id",
                            request.getSourceWarehouseId()));
            movement.setSourceWarehouse(source);
        }

        if (request.getDestinationWarehouseId() != null) {
            Warehouse destination = warehouseRepository
                    .findByTenantIdAndId(tenantId, request.getDestinationWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination warehouse", "id",
                            request.getDestinationWarehouseId()));
            movement.setDestinationWarehouse(destination);
        }

        // Create movement items
        List<StockMovementItem> items = new ArrayList<>();
        for (StockMovementItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findByTenantIdAndId(tenantId, itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemRequest.getProductId()));

            Batch batch = null;
            if (itemRequest.getBatchId() != null) {
                batch = batchRepository.findByTenantIdAndId(tenantId, itemRequest.getBatchId())
                        .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", itemRequest.getBatchId()));
            }

            StockMovementItem item = new StockMovementItem();
            item.setMovement(movement);
            item.setProduct(product);
            item.setBatch(batch);
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(itemRequest.getUnitPrice());

            if (itemRequest.getUnitPrice() != null) {
                item.setTotalPrice(itemRequest.getUnitPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
            }

            items.add(item);
        }

        movement.setItems(items);
        StockMovement saved = stockMovementRepository.save(movement);

        log.info("Created stock movement {} of type {} for tenant {}", saved.getId(), saved.getMovementType(),
                tenantId);

        return mapToResponse(saved);
    }

    @Transactional
    public StockMovementResponse executeMovement(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        StockMovement movement = stockMovementRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("StockMovement", "id", id));

        if (movement.getStatus() != MovementStatus.PENDING) {
            throw new BusinessException("Only pending movements can be executed");
        }

        try {
            // Apply stock changes based on movement type
            for (StockMovementItem item : movement.getItems()) {
                applyStockChanges(movement, item);
            }

            if (movement.getMovementType() == MovementType.TRANSFER) {
                movement.setStatus(MovementStatus.IN_TRANSIT);
                // completedAt will be set when validation is completed
            } else {
                movement.setStatus(MovementStatus.COMPLETED);
                movement.setCompletedAt(LocalDateTime.now());
            }

            StockMovement updated = stockMovementRepository.save(movement);
            log.info("Executed stock movement {} for tenant {}", id, tenantId);

            return mapToResponse(updated);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("Batch was modified by another transaction. Please retry.");
        }
    }

    @Transactional
    public StockMovementResponse cancelMovement(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        StockMovement movement = stockMovementRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("StockMovement", "id", id));

        if (movement.getStatus() == MovementStatus.COMPLETED) {
            throw new BusinessException("Cannot cancel completed movements");
        }

        movement.setStatus(MovementStatus.CANCELLED);
        StockMovement updated = stockMovementRepository.save(movement);

        log.info("Cancelled stock movement {} for tenant {}", id, tenantId);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return stockMovementRepository.findAllByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StockMovementResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        StockMovement movement = stockMovementRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("StockMovement", "id", id));
        return mapToResponse(movement);
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> findByType(MovementType movementType) {
        UUID tenantId = TenantContext.getTenantId();
        return stockMovementRepository.findByTenantIdAndMovementType(tenantId, movementType).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> findByStatus(MovementStatus status) {
        UUID tenantId = TenantContext.getTenantId();
        return stockMovementRepository.findByTenantIdAndStatus(tenantId, status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void validateWarehouses(StockMovementRequest request, UUID tenantId) {
        MovementType type = request.getMovementType();

        if (type == MovementType.TRANSFER) {
            if (request.getSourceWarehouseId() == null || request.getDestinationWarehouseId() == null) {
                throw new BusinessException("TRANSFER movements require both source and destination warehouses");
            }
            if (request.getSourceWarehouseId().equals(request.getDestinationWarehouseId())) {
                throw new BusinessException("Source and destination warehouses must be different");
            }
        } else if (type == MovementType.PURCHASE) {
            if (request.getDestinationWarehouseId() == null) {
                throw new BusinessException("PURCHASE movements require a destination warehouse");
            }
        } else if (type == MovementType.SALE) {
            if (request.getSourceWarehouseId() == null) {
                throw new BusinessException("SALE movements require a source warehouse");
            }
        }
    }

    private void applyStockChanges(StockMovement movement, StockMovementItem item) {
        MovementType type = movement.getMovementType();

        if (item.getBatch() == null) {
            throw new BusinessException("Batch is required to execute stock movements");
        }

        Batch batch = item.getBatch();

        switch (type) {
            case PURCHASE:
                // Increase stock in destination warehouse
                batch.setQuantity(batch.getQuantity() + item.getQuantity());
                batchRepository.save(batch);
                break;

            case SALE:
            case ADJUSTMENT:
                // Decrease stock from source warehouse
                if (batch.getQuantity() < item.getQuantity()) {
                    throw new BusinessException("Insufficient stock for product " + item.getProduct().getName());
                }
                batch.setQuantity(batch.getQuantity() - item.getQuantity());
                batchRepository.save(batch);
                break;

            case TRANSFER:
                // Only decrease from source - destination will be handled by validation
                if (batch.getQuantity() < item.getQuantity()) {
                    throw new BusinessException("Insufficient stock for product " + item.getProduct().getName());
                }
                batch.setQuantity(batch.getQuantity() - item.getQuantity());
                batchRepository.save(batch);
                // Note: Stock will be added to destination warehouse during validation
                break;

            case RETURN:
                // Increase stock (return to warehouse)
                batch.setQuantity(batch.getQuantity() + item.getQuantity());
                batchRepository.save(batch);
                break;
        }
    }

    private StockMovementResponse mapToResponse(StockMovement movement) {
        List<StockMovementItemResponse> itemResponses = movement.getItems().stream()
                .map(this::mapItemToResponse)
                .collect(Collectors.toList());

        return StockMovementResponse.builder()
                .id(movement.getId())
                .movementType(movement.getMovementType())
                .status(movement.getStatus())
                .sourceWarehouseId(movement.getSourceWarehouse() != null ? movement.getSourceWarehouse().getId() : null)
                .sourceWarehouseName(
                        movement.getSourceWarehouse() != null ? movement.getSourceWarehouse().getName() : null)
                .destinationWarehouseId(
                        movement.getDestinationWarehouse() != null ? movement.getDestinationWarehouse().getId() : null)
                .destinationWarehouseName(
                        movement.getDestinationWarehouse() != null ? movement.getDestinationWarehouse().getName()
                                : null)
                .userId(movement.getUser().getId())
                .userName(movement.getUser().getFullName())
                .notes(movement.getNotes())
                .items(itemResponses)
                .createdAt(movement.getCreatedAt())
                .updatedAt(movement.getUpdatedAt())
                .completedAt(movement.getCompletedAt())
                .build();
    }

    private StockMovementItemResponse mapItemToResponse(StockMovementItem item) {
        return StockMovementItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .batchId(item.getBatch() != null ? item.getBatch().getId() : null)
                .batchCode(item.getBatch() != null ? item.getBatch().getBatchCode() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .build();
    }
}
