package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.brand.BrandResponse;
import com.stockshift.backend.api.dto.brand.CreateBrandRequest;
import com.stockshift.backend.api.dto.brand.UpdateBrandRequest;
import com.stockshift.backend.api.mapper.BrandMapper;
import com.stockshift.backend.application.service.BrandService;
import com.stockshift.backend.domain.brand.Brand;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandControllerTest {

    @Mock
    private BrandService brandService;

    @Mock
    private BrandMapper brandMapper;

    @InjectMocks
    private BrandController brandController;

    private Brand brand;
    private BrandResponse brandResponse;

    @BeforeEach
    void setUp() {
        brand = new Brand();
        brand.setId(UUID.randomUUID());
        brand.setName("Test Brand");

        brandResponse = BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .build();
    }

    @Test
    void createBrandShouldReturnCreatedResponse() {
        CreateBrandRequest request = new CreateBrandRequest("Test Brand", "Description");
        when(brandService.createBrand(request)).thenReturn(brand);
        when(brandMapper.toResponse(brand)).thenReturn(brandResponse);

        ResponseEntity<BrandResponse> response = brandController.createBrand(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(brandResponse);
    }

    @Test
    void getAllBrandsShouldDelegateToActiveServiceWhenFlagTrue() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Brand> page = new PageImpl<>(List.of(brand), pageable, 1);
        when(brandService.getActiveBrands(pageable)).thenReturn(page);
        when(brandMapper.toResponse(brand)).thenReturn(brandResponse);

        ResponseEntity<Page<BrandResponse>> response = brandController.getAllBrands(true, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(brandResponse);
        verify(brandService).getActiveBrands(pageable);
    }

    @Test
    void updateBrandShouldReturnMappedResponse() {
        UUID id = UUID.randomUUID();
        UpdateBrandRequest request = new UpdateBrandRequest("Updated", "New Description", true);
        when(brandService.updateBrand(id, request)).thenReturn(brand);
        when(brandMapper.toResponse(brand)).thenReturn(brandResponse);

        ResponseEntity<BrandResponse> response = brandController.updateBrand(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(brandResponse);
    }

    @Test
    void deleteBrandShouldReturnNoContent() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = brandController.deleteBrand(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(brandService).deleteBrand(eq(id));
    }

    @Test
    void activateBrandShouldReturnUpdatedEntity() {
        UUID id = UUID.randomUUID();
        when(brandService.getBrandById(id)).thenReturn(brand);
        when(brandMapper.toResponse(brand)).thenReturn(brandResponse);

        ResponseEntity<BrandResponse> response = brandController.activateBrand(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(brandResponse);
        verify(brandService).activateBrand(id);
        verify(brandService).getBrandById(id);
    }
}
