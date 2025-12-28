package br.com.stockshift.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.stockshift.dto.warehouse.BatchRequest;
import br.com.stockshift.dto.warehouse.BatchResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchService {

    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional
    public BatchResponse create(BatchRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Validate unique batch code
        batchRepository.findByTenantIdAndBatchCode(tenantId, request.getBatchCode())
                .ifPresent(b -> {
                    throw new BusinessException("Batch with code " + request.getBatchCode() + " already exists");
                });

        // Validate product
        Product product = productRepository.findByTenantIdAndId(tenantId, request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        // Validate warehouse
        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", request.getWarehouseId()));

        // Validate expiration date if product has expiration
        if (product.getHasExpiration() && request.getExpirationDate() == null) {
            throw new BusinessException("Expiration date is required for products with expiration tracking");
        }

        Batch batch = new Batch();
        batch.setTenantId(tenantId);
        batch.setProduct(product);
        batch.setWarehouse(warehouse);
        batch.setBatchCode(request.getBatchCode());
        batch.setQuantity(request.getQuantity());
        batch.setManufacturedDate(request.getManufacturedDate());
        batch.setExpirationDate(request.getExpirationDate());
        batch.setCostPrice(request.getCostPrice());
        batch.setSellingPrice(request.getSellingPrice());

        Batch saved = batchRepository.save(batch);
        log.info("Created batch {} for tenant {}", saved.getId(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return batchRepository.findAllByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BatchResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Batch batch = batchRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", id));
        return mapToResponse(batch);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> findByWarehouse(UUID warehouseId) {
        UUID tenantId = TenantContext.getTenantId();
        return batchRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> findByProduct(UUID productId) {
        UUID tenantId = TenantContext.getTenantId();
        return batchRepository.findByProductIdAndTenantId(productId, tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> findExpiringBatches(Integer daysAhead) {
        UUID tenantId = TenantContext.getTenantId();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);

        return batchRepository.findExpiringBatches(startDate, endDate, tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> findLowStock(Integer threshold) {
        UUID tenantId = TenantContext.getTenantId();
        return batchRepository.findLowStock(threshold, tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BatchResponse update(UUID id, BatchRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        Batch batch = batchRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", id));

        // Validate unique batch code if changed
        if (!batch.getBatchCode().equals(request.getBatchCode())) {
            batchRepository.findByTenantIdAndBatchCode(tenantId, request.getBatchCode())
                    .ifPresent(b -> {
                        throw new BusinessException("Batch with code " + request.getBatchCode() + " already exists");
                    });
        }

        // Validate product if changed
        if (!batch.getProduct().getId().equals(request.getProductId())) {
            Product product = productRepository.findByTenantIdAndId(tenantId, request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));
            batch.setProduct(product);
        }

        // Validate warehouse if changed
        if (!batch.getWarehouse().getId().equals(request.getWarehouseId())) {
            Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, request.getWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", request.getWarehouseId()));
            batch.setWarehouse(warehouse);
        }

        batch.setBatchCode(request.getBatchCode());
        batch.setQuantity(request.getQuantity());
        batch.setManufacturedDate(request.getManufacturedDate());
        batch.setExpirationDate(request.getExpirationDate());
        batch.setCostPrice(request.getCostPrice());
        batch.setSellingPrice(request.getSellingPrice());

        try {
            Batch updated = batchRepository.save(batch);
            log.info("Updated batch {} for tenant {}", id, tenantId);
            return mapToResponse(updated);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException("Batch was modified by another transaction. Please retry.");
        }
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Batch batch = batchRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", id));

        batchRepository.delete(batch);
        log.info("Deleted batch {} for tenant {}", id, tenantId);
    }

    private BatchResponse mapToResponse(Batch batch) {
        return BatchResponse.builder()
                .id(batch.getId())
                .productId(batch.getProduct().getId())
                .productName(batch.getProduct().getName())
                .warehouseId(batch.getWarehouse().getId())
                .warehouseName(batch.getWarehouse().getName())
                .batchCode(batch.getBatchCode())
                .quantity(batch.getQuantity())
                .manufacturedDate(batch.getManufacturedDate())
                .expirationDate(batch.getExpirationDate())
                .costPrice(batch.getCostPrice())
                .sellingPrice(batch.getSellingPrice())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }
}
