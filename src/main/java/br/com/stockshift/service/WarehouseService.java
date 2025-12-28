package br.com.stockshift.service;

import br.com.stockshift.dto.warehouse.WarehouseRequest;
import br.com.stockshift.dto.warehouse.WarehouseResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Validate unique name
        warehouseRepository.findByTenantIdAndName(tenantId, request.getName())
                .ifPresent(w -> {
                    throw new BusinessException("Warehouse with name " + request.getName() + " already exists");
                });

        Warehouse warehouse = new Warehouse();
        warehouse.setTenantId(tenantId);
        warehouse.setName(request.getName());
        warehouse.setCity(request.getCity());
        warehouse.setState(request.getState());
        warehouse.setAddress(request.getAddress());
        warehouse.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Created warehouse {} for tenant {}", saved.getId(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return warehouseRepository.findAllByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", id));
        return mapToResponse(warehouse);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findActive(Boolean isActive) {
        UUID tenantId = TenantContext.getTenantId();
        return warehouseRepository.findByTenantIdAndIsActive(tenantId, isActive).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public WarehouseResponse update(UUID id, WarehouseRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", id));

        // Validate unique name if changed
        if (!warehouse.getName().equals(request.getName())) {
            warehouseRepository.findByTenantIdAndName(tenantId, request.getName())
                    .ifPresent(w -> {
                        throw new BusinessException("Warehouse with name " + request.getName() + " already exists");
                    });
        }

        warehouse.setName(request.getName());
        warehouse.setCity(request.getCity());
        warehouse.setState(request.getState());
        warehouse.setAddress(request.getAddress());
        warehouse.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        Warehouse updated = warehouseRepository.save(warehouse);
        log.info("Updated warehouse {} for tenant {}", id, tenantId);

        return mapToResponse(updated);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Warehouse warehouse = warehouseRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", id));

        warehouseRepository.delete(warehouse);
        log.info("Deleted warehouse {} for tenant {}", id, tenantId);
    }

    private WarehouseResponse mapToResponse(Warehouse warehouse) {
        return WarehouseResponse.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .city(warehouse.getCity())
                .state(warehouse.getState())
                .address(warehouse.getAddress())
                .isActive(warehouse.getIsActive())
                .createdAt(warehouse.getCreatedAt())
                .updatedAt(warehouse.getUpdatedAt())
                .build();
    }
}
