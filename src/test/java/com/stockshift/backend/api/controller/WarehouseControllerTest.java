package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.warehouse.CreateWarehouseRequest;
import com.stockshift.backend.api.dto.warehouse.UpdateWarehouseRequest;
import com.stockshift.backend.api.dto.warehouse.WarehouseResponse;
import com.stockshift.backend.api.mapper.WarehouseMapper;
import com.stockshift.backend.application.service.WarehouseService;
import com.stockshift.backend.domain.warehouse.Warehouse;
import com.stockshift.backend.domain.warehouse.WarehouseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseControllerTest {

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private WarehouseMapper warehouseMapper;

    @InjectMocks
    private WarehouseController warehouseController;

    private Warehouse warehouse;
    private WarehouseResponse warehouseResponse;

    @BeforeEach
    void setUp() {
        warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setCode("WH-1");

        warehouseResponse = new WarehouseResponse();
        warehouseResponse.setId(warehouse.getId());
        warehouseResponse.setCode(warehouse.getCode());
    }

    @Test
    void createWarehouseShouldReturnCreatedResponse() {
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH-1",
                "Warehouse",
                null,
                WarehouseType.STORE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(warehouseService.createWarehouse(request)).thenReturn(warehouse);
        when(warehouseMapper.toResponse(warehouse)).thenReturn(warehouseResponse);

        ResponseEntity<WarehouseResponse> response = warehouseController.createWarehouse(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(warehouseResponse);
    }

    @Test
    void getAllWarehousesShouldUseActiveFlagWhenTrue() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Warehouse> page = new PageImpl<>(List.of(warehouse));
        when(warehouseService.getActiveWarehouses(pageable)).thenReturn(page);
        when(warehouseMapper.toResponse(warehouse)).thenReturn(warehouseResponse);

        ResponseEntity<Page<WarehouseResponse>> response = warehouseController.getAllWarehouses(true, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(warehouseResponse);
        verify(warehouseService).getActiveWarehouses(pageable);
    }

    @Test
    void getWarehousesByTypeShouldDelegateToService() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Warehouse> page = new PageImpl<>(List.of(warehouse));
        when(warehouseService.getWarehousesByType(WarehouseType.STORE, true, pageable)).thenReturn(page);
        when(warehouseMapper.toResponse(warehouse)).thenReturn(warehouseResponse);

        ResponseEntity<Page<WarehouseResponse>> response = warehouseController.getWarehousesByType(WarehouseType.STORE, true, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(warehouseResponse);
    }

    @Test
    void activateWarehouseShouldReturnMappedResponse() {
        UUID id = UUID.randomUUID();
        when(warehouseService.getWarehouseById(id)).thenReturn(warehouse);
        when(warehouseMapper.toResponse(warehouse)).thenReturn(warehouseResponse);

        ResponseEntity<WarehouseResponse> response = warehouseController.activateWarehouse(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(warehouseResponse);
        verify(warehouseService).activateWarehouse(id);
    }
}
