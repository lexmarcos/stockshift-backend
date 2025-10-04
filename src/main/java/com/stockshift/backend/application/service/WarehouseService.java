package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.warehouse.CreateWarehouseRequest;
import com.stockshift.backend.api.dto.warehouse.UpdateWarehouseRequest;
import com.stockshift.backend.api.mapper.WarehouseMapper;
import com.stockshift.backend.domain.warehouse.Warehouse;
import com.stockshift.backend.domain.warehouse.WarehouseType;
import com.stockshift.backend.domain.warehouse.exception.WarehouseAlreadyExistsException;
import com.stockshift.backend.domain.warehouse.exception.WarehouseNotFoundException;
import com.stockshift.backend.infrastructure.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper mapper;

    @Transactional
    public Warehouse createWarehouse(CreateWarehouseRequest request) {
        String code = request.getCode().toUpperCase();
        
        if (warehouseRepository.existsByCode(code)) {
            throw new WarehouseAlreadyExistsException(code);
        }

        Warehouse warehouse = mapper.toEntity(request);
        return warehouseRepository.save(warehouse);
    }

    @Transactional(readOnly = true)
    public Page<Warehouse> getAllWarehouses(Pageable pageable) {
        return warehouseRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Warehouse> getActiveWarehouses(Pageable pageable) {
        return warehouseRepository.findAllByActiveTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Warehouse> getWarehousesByType(WarehouseType type, Boolean onlyActive, Pageable pageable) {
        if (onlyActive != null && onlyActive) {
            return warehouseRepository.findAllByTypeAndActiveTrue(type, pageable);
        }
        return warehouseRepository.findAllByType(type, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Warehouse> searchWarehouses(String query, Pageable pageable) {
        return warehouseRepository.findAllByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(query, query, pageable);
    }

    @Transactional(readOnly = true)
    public Warehouse getWarehouseById(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new WarehouseNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Warehouse getWarehouseByCode(String code) {
        return warehouseRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new WarehouseNotFoundException(code));
    }

    @Transactional
    public Warehouse updateWarehouse(UUID id, UpdateWarehouseRequest request) {
        Warehouse warehouse = getWarehouseById(id);
        mapper.updateEntity(request, warehouse);
        return warehouseRepository.save(warehouse);
    }

    @Transactional
    public void deactivateWarehouse(UUID id) {
        Warehouse warehouse = getWarehouseById(id);
        warehouse.setActive(false);
        warehouseRepository.save(warehouse);
    }

    @Transactional
    public void activateWarehouse(UUID id) {
        Warehouse warehouse = getWarehouseById(id);
        warehouse.setActive(true);
        warehouseRepository.save(warehouse);
    }
}
