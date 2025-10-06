package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.warehouse.CreateWarehouseRequest;
import com.stockshift.backend.api.dto.warehouse.UpdateWarehouseRequest;
import com.stockshift.backend.api.mapper.WarehouseMapper;
import com.stockshift.backend.domain.warehouse.Warehouse;
import com.stockshift.backend.domain.warehouse.WarehouseType;
import com.stockshift.backend.domain.warehouse.exception.WarehouseAlreadyExistsException;
import com.stockshift.backend.domain.warehouse.exception.WarehouseNotFoundException;
import com.stockshift.backend.infrastructure.repository.WarehouseRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private WarehouseMapper warehouseMapper;

    @InjectMocks
    private WarehouseService warehouseService;

    private Warehouse testWarehouse;
    private CreateWarehouseRequest createWarehouseRequest;
    private UpdateWarehouseRequest updateWarehouseRequest;

    @BeforeEach
    void setUp() {
        testWarehouse = new Warehouse();
        testWarehouse.setId(UUID.randomUUID());
        testWarehouse.setCode("WH001");
        testWarehouse.setName("Test Warehouse");
        testWarehouse.setType(WarehouseType.DISTRIBUTION);
        testWarehouse.setAddress("Test Address");
        testWarehouse.setActive(true);

        createWarehouseRequest = new CreateWarehouseRequest();
        createWarehouseRequest.setCode("wh002");
        createWarehouseRequest.setName("New Warehouse");
        createWarehouseRequest.setType(WarehouseType.STORE);
        createWarehouseRequest.setAddress("New Address");
        createWarehouseRequest.setCity("Test City");
        createWarehouseRequest.setState("Test State");
        createWarehouseRequest.setCountry("Test Country");
        createWarehouseRequest.setPostalCode("12345");

        updateWarehouseRequest = new UpdateWarehouseRequest();
        updateWarehouseRequest.setName("Updated Warehouse");
        updateWarehouseRequest.setAddress("Updated Address");
    }

    @Test
    void shouldCreateWarehouseSuccessfully() {
        // Given
        when(warehouseRepository.existsByCode("WH002")).thenReturn(false);
        when(warehouseMapper.toEntity(createWarehouseRequest)).thenReturn(testWarehouse);
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse);

        // When
        Warehouse createdWarehouse = warehouseService.createWarehouse(createWarehouseRequest);

        // Then
        assertThat(createdWarehouse).isNotNull();
        assertThat(createdWarehouse.getId()).isNotNull();
        verify(warehouseRepository).existsByCode("WH002");
        verify(warehouseMapper).toEntity(createWarehouseRequest);
        verify(warehouseRepository).save(any(Warehouse.class));
    }

    @Test
    void shouldConvertCodeToUpperCaseWhenCreating() {
        // Given
        when(warehouseRepository.existsByCode("WH002")).thenReturn(false);
        when(warehouseMapper.toEntity(createWarehouseRequest)).thenReturn(testWarehouse);
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse);

        // When
        warehouseService.createWarehouse(createWarehouseRequest);

        // Then
        verify(warehouseRepository).existsByCode("WH002"); // Should be uppercase
    }

    @Test
    void shouldThrowExceptionWhenWarehouseCodeAlreadyExists() {
        // Given
        when(warehouseRepository.existsByCode("WH002")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> warehouseService.createWarehouse(createWarehouseRequest))
                .isInstanceOf(WarehouseAlreadyExistsException.class);

        verify(warehouseRepository).existsByCode("WH002");
        verify(warehouseMapper, never()).toEntity(any());
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void shouldGetAllWarehousesSuccessfully() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Warehouse> warehousePage = new PageImpl<>(List.of(testWarehouse));
        when(warehouseRepository.findAll(pageable)).thenReturn(warehousePage);

        // When
        Page<Warehouse> result = warehouseService.getAllWarehouses(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(testWarehouse);
        verify(warehouseRepository).findAll(pageable);
    }

    @Test
    void shouldGetActiveWarehousesSuccessfully() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Warehouse> warehousePage = new PageImpl<>(List.of(testWarehouse));
        when(warehouseRepository.findAllByActiveTrue(pageable)).thenReturn(warehousePage);

        // When
        Page<Warehouse> result = warehouseService.getActiveWarehouses(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(warehouseRepository).findAllByActiveTrue(pageable);
    }

    @Test
    void shouldGetWarehousesByTypeWithActiveFilter() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        WarehouseType type = WarehouseType.STORE;
        Page<Warehouse> warehousePage = new PageImpl<>(List.of(testWarehouse));
        when(warehouseRepository.findAllByTypeAndActiveTrue(type, pageable)).thenReturn(warehousePage);

        // When
        Page<Warehouse> result = warehouseService.getWarehousesByType(type, true, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(warehouseRepository).findAllByTypeAndActiveTrue(type, pageable);
        verify(warehouseRepository, never()).findAllByType(any(), any());
    }

    @Test
    void shouldGetWarehousesByTypeWithoutActiveFilter() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        WarehouseType type = WarehouseType.DISTRIBUTION;
        Page<Warehouse> warehousePage = new PageImpl<>(List.of(testWarehouse));
        when(warehouseRepository.findAllByType(type, pageable)).thenReturn(warehousePage);

        // When
        Page<Warehouse> result = warehouseService.getWarehousesByType(type, false, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(warehouseRepository).findAllByType(type, pageable);
        verify(warehouseRepository, never()).findAllByTypeAndActiveTrue(any(), any());
    }

    @Test
    void shouldGetWarehousesByTypeWhenActiveFilterIsNull() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        WarehouseType type = WarehouseType.STORE;
        Page<Warehouse> warehousePage = new PageImpl<>(List.of(testWarehouse));
        when(warehouseRepository.findAllByType(type, pageable)).thenReturn(warehousePage);

        // When
        Page<Warehouse> result = warehouseService.getWarehousesByType(type, null, pageable);

        // Then
        assertThat(result).isNotNull();
        verify(warehouseRepository).findAllByType(type, pageable);
    }

    @Test
    void shouldSearchWarehousesSuccessfully() {
        // Given
        String query = "test";
        Pageable pageable = PageRequest.of(0, 10);
        Page<Warehouse> warehousePage = new PageImpl<>(List.of(testWarehouse));
        when(warehouseRepository.findAllByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(query, query, pageable))
                .thenReturn(warehousePage);

        // When
        Page<Warehouse> result = warehouseService.searchWarehouses(query, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(warehouseRepository).findAllByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(query, query, pageable);
    }

    @Test
    void shouldGetWarehouseByIdSuccessfully() {
        // Given
        UUID warehouseId = testWarehouse.getId();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(testWarehouse));

        // When
        Warehouse foundWarehouse = warehouseService.getWarehouseById(warehouseId);

        // Then
        assertThat(foundWarehouse).isNotNull();
        assertThat(foundWarehouse.getId()).isEqualTo(warehouseId);
        verify(warehouseRepository).findById(warehouseId);
    }

    @Test
    void shouldThrowExceptionWhenWarehouseNotFoundById() {
        // Given
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> warehouseService.getWarehouseById(warehouseId))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(warehouseRepository).findById(warehouseId);
    }

    @Test
    void shouldGetWarehouseByCodeSuccessfully() {
        // Given
        String code = "wh001";
        when(warehouseRepository.findByCode("WH001")).thenReturn(Optional.of(testWarehouse));

        // When
        Warehouse foundWarehouse = warehouseService.getWarehouseByCode(code);

        // Then
        assertThat(foundWarehouse).isNotNull();
        assertThat(foundWarehouse.getCode()).isEqualTo("WH001");
        verify(warehouseRepository).findByCode("WH001"); // Should be uppercase
    }

    @Test
    void shouldThrowExceptionWhenWarehouseNotFoundByCode() {
        // Given
        String code = "NONEXISTENT";
        when(warehouseRepository.findByCode(code)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> warehouseService.getWarehouseByCode(code))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(warehouseRepository).findByCode(code);
    }

    @Test
    void shouldUpdateWarehouseSuccessfully() {
        // Given
        UUID warehouseId = testWarehouse.getId();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(testWarehouse));
        doNothing().when(warehouseMapper).updateEntity(updateWarehouseRequest, testWarehouse);
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse);

        // When
        Warehouse updatedWarehouse = warehouseService.updateWarehouse(warehouseId, updateWarehouseRequest);

        // Then
        assertThat(updatedWarehouse).isNotNull();
        verify(warehouseRepository).findById(warehouseId);
        verify(warehouseMapper).updateEntity(updateWarehouseRequest, testWarehouse);
        verify(warehouseRepository).save(testWarehouse);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentWarehouse() {
        // Given
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> warehouseService.updateWarehouse(warehouseId, updateWarehouseRequest))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(warehouseRepository).findById(warehouseId);
        verify(warehouseMapper, never()).updateEntity(any(), any());
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void shouldDeactivateWarehouseSuccessfully() {
        // Given
        UUID warehouseId = testWarehouse.getId();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(testWarehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse);

        // When
        warehouseService.deactivateWarehouse(warehouseId);

        // Then
        assertThat(testWarehouse.getActive()).isFalse();
        verify(warehouseRepository).findById(warehouseId);
        verify(warehouseRepository).save(testWarehouse);
    }

    @Test
    void shouldActivateWarehouseSuccessfully() {
        // Given
        UUID warehouseId = testWarehouse.getId();
        testWarehouse.setActive(false);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(testWarehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(testWarehouse);

        // When
        warehouseService.activateWarehouse(warehouseId);

        // Then
        assertThat(testWarehouse.getActive()).isTrue();
        verify(warehouseRepository).findById(warehouseId);
        verify(warehouseRepository).save(testWarehouse);
    }

    @Test
    void shouldThrowExceptionWhenDeactivatingNonExistentWarehouse() {
        // Given
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> warehouseService.deactivateWarehouse(warehouseId))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(warehouseRepository).findById(warehouseId);
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void shouldThrowExceptionWhenActivatingNonExistentWarehouse() {
        // Given
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> warehouseService.activateWarehouse(warehouseId))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(warehouseRepository).findById(warehouseId);
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }
}
