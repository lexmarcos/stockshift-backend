package br.com.stockshift.service;

import br.com.stockshift.dto.warehouse.WarehouseRequest;
import br.com.stockshift.dto.warehouse.WarehouseResponse;
import br.com.stockshift.dto.warehouse.WarehouseStockSummaryProjection;
import br.com.stockshift.dto.warehouse.WarehouseStockSummaryResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.audit.AuditEventCreateRequest;
import br.com.stockshift.service.audit.AuditService;
import br.com.stockshift.service.audit.AuditSnapshotService;
import br.com.stockshift.util.SanitizationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import br.com.stockshift.dto.warehouse.ProductWithStockResponse;
import br.com.stockshift.dto.warehouse.ProductWithStockProjection;
import br.com.stockshift.dto.brand.BrandResponse;
import br.com.stockshift.repository.BatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final BatchRepository batchRepository;
    private final WarehouseAccessService warehouseAccessService;
    private final AuditService auditService;
    private final AuditSnapshotService auditSnapshotService;

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Sanitize input to prevent XSS
        String sanitizedName = SanitizationUtil.sanitizeForHtml(request.getName());
        String sanitizedCity = SanitizationUtil.sanitizeForHtml(request.getCity());
        String sanitizedAddress = SanitizationUtil.sanitizeForHtml(request.getAddress());

        // Generate code if not provided
        String code;
        if (request.getCode() != null && !request.getCode().isBlank()) {
            code = SanitizationUtil.sanitizeForHtml(request.getCode());
        } else {
            code = generateWarehouseCode(sanitizedName, sanitizedCity, tenantId);
        }

        // Validate unique name
        warehouseRepository.findByTenantIdAndName(tenantId, sanitizedName)
                .ifPresent(w -> {
                    throw new BusinessException("Warehouse with name " + sanitizedName + " already exists");
                });

        // Validate unique code
        warehouseRepository.findByTenantIdAndCode(tenantId, code)
                .ifPresent(w -> {
                    throw new BusinessException("Warehouse with code " + code + " already exists");
                });

        Warehouse warehouse = new Warehouse();
        warehouse.setTenantId(tenantId);
        warehouse.setName(sanitizedName);
        warehouse.setCode(code);
        warehouse.setCity(sanitizedCity);
        warehouse.setState(request.getState());
        warehouse.setAddress(sanitizedAddress);
        warehouse.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        Warehouse saved = warehouseRepository.save(warehouse);
        recordWarehouseAudit("WAREHOUSE_CREATED", null, auditSnapshotService.snapshot(saved), saved.getId());
        log.info("Created warehouse {} for tenant {}", saved.getId(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();

        List<Warehouse> warehouses;
        if (warehouseAccessService.hasFullAccess()) {
            warehouses = warehouseRepository.findAllByTenantId(tenantId);
        } else {
            Set<UUID> userWarehouseIds = warehouseAccessService.getUserWarehouseIds();
            warehouses = warehouseRepository.findAllByTenantId(tenantId).stream()
                    .filter(w -> userWarehouseIds.contains(w.getId()))
                    .collect(Collectors.toList());
        }

        return warehouses.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WarehouseStockSummaryResponse> getStockSummaries() {
        UUID tenantId = TenantContext.getTenantId();
        Set<UUID> accessibleWarehouseIds = resolveAccessibleWarehouseIds(tenantId);

        if (accessibleWarehouseIds.isEmpty()) {
            return List.of();
        }

        return batchRepository.findStockSummaryByWarehouseIds(tenantId, accessibleWarehouseIds).stream()
                .map(this::mapToStockSummaryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        warehouseAccessService.validateWarehouseAccess(id);

        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", id));
        return mapToResponse(warehouse);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findActive(Boolean isActive) {
        UUID tenantId = TenantContext.getTenantId();
        List<Warehouse> warehouses = warehouseRepository.findByTenantIdAndIsActive(tenantId, isActive);

        if (!warehouseAccessService.hasFullAccess()) {
            Set<UUID> userWarehouseIds = warehouseAccessService.getUserWarehouseIds();
            warehouses = warehouses.stream()
                    .filter(w -> userWarehouseIds.contains(w.getId()))
                    .collect(Collectors.toList());
        }

        return warehouses.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public WarehouseResponse update(UUID id, WarehouseRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        warehouseAccessService.validateWarehouseAccess(id);

        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", id));
        var before = auditSnapshotService.snapshot(warehouse);

        // Sanitize input to prevent XSS
        String sanitizedName = SanitizationUtil.sanitizeForHtml(request.getName());
        String sanitizedCity = SanitizationUtil.sanitizeForHtml(request.getCity());
        String sanitizedAddress = SanitizationUtil.sanitizeForHtml(request.getAddress());
        String sanitizedCode = SanitizationUtil.sanitizeForHtml(request.getCode());

        // Validate unique name if changed
        if (!warehouse.getName().equals(sanitizedName)) {
            warehouseRepository.findByTenantIdAndName(tenantId, sanitizedName)
                    .ifPresent(w -> {
                        throw new BusinessException("Warehouse with name " + sanitizedName + " already exists");
                    });
        }

        // Validate unique code if changed
        if (!warehouse.getCode().equals(sanitizedCode)) {
            warehouseRepository.findByTenantIdAndCode(tenantId, sanitizedCode)
                    .ifPresent(w -> {
                        throw new BusinessException("Warehouse with code " + sanitizedCode + " already exists");
                    });
        }

        warehouse.setName(sanitizedName);
        warehouse.setCode(sanitizedCode);
        warehouse.setCity(sanitizedCity);
        warehouse.setState(request.getState());
        warehouse.setAddress(sanitizedAddress);
        warehouse.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        Warehouse updated = warehouseRepository.save(warehouse);
        var after = auditSnapshotService.snapshot(updated);
        recordWarehouseAudit("WAREHOUSE_UPDATED", before, after, updated.getId());
        log.info("Updated warehouse {} for tenant {}", id, tenantId);

        return mapToResponse(updated);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        warehouseAccessService.validateWarehouseAccess(id);

        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", id));

        var before = auditSnapshotService.snapshot(warehouse);
        warehouseRepository.delete(warehouse);
        recordWarehouseAudit("WAREHOUSE_DELETED", before, null, id);
        log.info("Deleted warehouse {} for tenant {}", id, tenantId);
    }

    private void recordWarehouseAudit(
            String action,
            java.util.Map<String, Object> before,
            java.util.Map<String, Object> after,
            UUID warehouseId) {
        auditService.record(AuditEventCreateRequest.builder()
                .operation(AuditService.OPERATION_TECHNICAL)
                .action(action)
                .outcome(AuditService.OUTCOME_SUCCESS)
                .resourceType("WAREHOUSE")
                .resourceId(warehouseId.toString())
                .beforeState(before)
                .afterState(after)
                .changedFields(auditSnapshotService.diff(before, after))
                .build());
    }

    private String generateWarehouseCode(String name, String city, UUID tenantId) {
        // Extract prefix from name (first 3 letters, uppercase)
        String namePrefix = name.replaceAll("[^a-zA-Z]", "")
                .toUpperCase()
                .substring(0, Math.min(3, name.replaceAll("[^a-zA-Z]", "").length()));

        // Extract city prefix (first 2 letters, uppercase)
        String cityPrefix = city.replaceAll("[^a-zA-Z]", "")
                .toUpperCase()
                .substring(0, Math.min(2, city.replaceAll("[^a-zA-Z]", "").length()));

        String baseCode = namePrefix + "-" + cityPrefix;

        // Find existing warehouse codes
        List<Warehouse> existingWarehouses = warehouseRepository.findAllByTenantId(tenantId);
        Set<String> existingCodes = existingWarehouses.stream()
                .map(Warehouse::getCode)
                .collect(Collectors.toSet());

        // Check if base code is available
        if (!existingCodes.contains(baseCode)) {
            return baseCode;
        }

        // Find next available suffix
        int suffix = 2;
        while (existingCodes.contains(baseCode + "-" + String.format("%02d", suffix))) {
            suffix++;
        }

        return baseCode + "-" + String.format("%02d", suffix);
    }

    private WarehouseResponse mapToResponse(Warehouse warehouse) {
        return WarehouseResponse.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .code(warehouse.getCode())
                .city(warehouse.getCity())
                .state(warehouse.getState())
                .address(warehouse.getAddress())
                .isActive(warehouse.getIsActive())
                .createdAt(warehouse.getCreatedAt())
                .updatedAt(warehouse.getUpdatedAt())
                .build();
    }

    private WarehouseStockSummaryResponse mapToStockSummaryResponse(WarehouseStockSummaryProjection projection) {
        return WarehouseStockSummaryResponse.builder()
                .warehouseId(projection.getWarehouseId())
                .productCount(projection.getProductCount())
                .batchCount(projection.getBatchCount())
                .totalQuantity(projection.getTotalQuantity())
                .build();
    }

    private Set<UUID> resolveAccessibleWarehouseIds(UUID tenantId) {
        if (warehouseAccessService.hasFullAccess()) {
            return warehouseRepository.findAllByTenantId(tenantId).stream()
                    .map(Warehouse::getId)
                    .collect(Collectors.toSet());
        }

        return warehouseAccessService.getUserWarehouseIds();
    }

    @Transactional(readOnly = true)
    public Page<ProductWithStockResponse> getProductsWithStock(UUID warehouseId, String search, Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        warehouseAccessService.validateWarehouseAccess(warehouseId);

        // Validate warehouse exists and belongs to tenant
        warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", warehouseId));

        String sanitizedSearch = (search != null && !search.isBlank())
                ? SanitizationUtil.sanitizeForHtml(search.trim())
                : "";

        // Fetch products with aggregated stock
        Page<ProductWithStockProjection> projections = batchRepository.findProductsWithStockByWarehouse(warehouseId,
                tenantId, sanitizedSearch, pageable);

        // Map to response DTO
        return projections.map(this::mapToProductWithStockResponse);
    }

    private ProductWithStockResponse mapToProductWithStockResponse(ProductWithStockProjection projection) {
        BrandResponse brandResponse = null;
        if (projection.getBrand() != null) {
            brandResponse = BrandResponse.builder()
                    .id(projection.getBrand().getId())
                    .name(projection.getBrand().getName())
                    .logoUrl(projection.getBrand().getLogoUrl())
                    .createdAt(projection.getBrand().getCreatedAt())
                    .updatedAt(projection.getBrand().getUpdatedAt())
                    .build();
        }

        return ProductWithStockResponse.builder()
                .id(projection.getId())
                .name(projection.getName())
                .sku(projection.getSku())
                .barcode(projection.getBarcode())
                .barcodeType(projection.getBarcodeType())
                .description(projection.getDescription())
                .categoryId(projection.getCategory() != null ? projection.getCategory().getId() : null)
                .categoryName(projection.getCategory() != null ? projection.getCategory().getName() : null)
                .brand(brandResponse)
                .isKit(projection.getIsKit())
                .attributes(projection.getAttributes())
                .hasExpiration(projection.getHasExpiration())
                .active(projection.getActive())
                .totalQuantity(projection.getTotalQuantity())
                .createdAt(projection.getCreatedAt())
                .updatedAt(projection.getUpdatedAt())
                .build();
    }
}
