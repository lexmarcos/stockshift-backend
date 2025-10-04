package com.stockshift.backend.api.mapper;

import com.stockshift.backend.api.dto.warehouse.CreateWarehouseRequest;
import com.stockshift.backend.api.dto.warehouse.UpdateWarehouseRequest;
import com.stockshift.backend.api.dto.warehouse.WarehouseResponse;
import com.stockshift.backend.domain.warehouse.Warehouse;
import org.springframework.stereotype.Component;

@Component
public class WarehouseMapper {

    public Warehouse toEntity(CreateWarehouseRequest request) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(request.getCode().toUpperCase());
        warehouse.setName(request.getName());
        warehouse.setDescription(request.getDescription());
        warehouse.setType(request.getType());
        warehouse.setAddress(request.getAddress());
        warehouse.setCity(request.getCity());
        warehouse.setState(request.getState() != null ? request.getState().toUpperCase() : null);
        warehouse.setPostalCode(request.getPostalCode());
        warehouse.setCountry(request.getCountry());
        warehouse.setPhone(request.getPhone());
        warehouse.setEmail(request.getEmail());
        warehouse.setManagerName(request.getManagerName());
        warehouse.setActive(true);
        return warehouse;
    }

    public void updateEntity(UpdateWarehouseRequest request, Warehouse warehouse) {
        if (request.getName() != null) {
            warehouse.setName(request.getName());
        }
        if (request.getDescription() != null) {
            warehouse.setDescription(request.getDescription());
        }
        if (request.getType() != null) {
            warehouse.setType(request.getType());
        }
        if (request.getAddress() != null) {
            warehouse.setAddress(request.getAddress());
        }
        if (request.getCity() != null) {
            warehouse.setCity(request.getCity());
        }
        if (request.getState() != null) {
            warehouse.setState(request.getState().toUpperCase());
        }
        if (request.getPostalCode() != null) {
            warehouse.setPostalCode(request.getPostalCode());
        }
        if (request.getCountry() != null) {
            warehouse.setCountry(request.getCountry());
        }
        if (request.getPhone() != null) {
            warehouse.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            warehouse.setEmail(request.getEmail());
        }
        if (request.getManagerName() != null) {
            warehouse.setManagerName(request.getManagerName());
        }
    }

    public WarehouseResponse toResponse(Warehouse warehouse) {
        WarehouseResponse response = new WarehouseResponse();
        response.setId(warehouse.getId());
        response.setCode(warehouse.getCode());
        response.setName(warehouse.getName());
        response.setDescription(warehouse.getDescription());
        response.setType(warehouse.getType());
        response.setAddress(warehouse.getAddress());
        response.setCity(warehouse.getCity());
        response.setState(warehouse.getState());
        response.setPostalCode(warehouse.getPostalCode());
        response.setCountry(warehouse.getCountry());
        response.setPhone(warehouse.getPhone());
        response.setEmail(warehouse.getEmail());
        response.setManagerName(warehouse.getManagerName());
        response.setActive(warehouse.getActive());
        response.setCreatedAt(warehouse.getCreatedAt());
        response.setUpdatedAt(warehouse.getUpdatedAt());
        return response;
    }
}
