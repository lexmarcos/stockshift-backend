package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.brand.CreateBrandRequest;
import com.stockshift.backend.api.dto.brand.UpdateBrandRequest;
import com.stockshift.backend.domain.brand.Brand;
import com.stockshift.backend.domain.brand.exception.BrandAlreadyExistsException;
import com.stockshift.backend.domain.brand.exception.BrandNotFoundException;
import com.stockshift.backend.infrastructure.repository.BrandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandService brandService;

    private Brand testBrand;

    @BeforeEach
    void setUp() {
        testBrand = new Brand();
        testBrand.setId(UUID.randomUUID());
        testBrand.setName("Test Brand");
        testBrand.setDescription("Test brand description");
        testBrand.setActive(true);
    }

    @Test
    void shouldCreateBrandSuccessfully() {
        CreateBrandRequest request = new CreateBrandRequest("New Brand", "New description");

        when(brandRepository.existsByName(request.getName())).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenReturn(testBrand);

        Brand createdBrand = brandService.createBrand(request);

        assertThat(createdBrand).isNotNull();
        assertThat(createdBrand.getId()).isEqualTo(testBrand.getId());
        verify(brandRepository).existsByName(request.getName());
        verify(brandRepository).save(any(Brand.class));
    }

    @Test
    void shouldThrowExceptionWhenBrandAlreadyExistsOnCreate() {
        CreateBrandRequest request = new CreateBrandRequest("Existing Brand", "Description");

        when(brandRepository.existsByName(request.getName())).thenReturn(true);

        assertThatThrownBy(() -> brandService.createBrand(request))
                .isInstanceOf(BrandAlreadyExistsException.class)
                .hasMessageContaining("Brand already exists with name");

        verify(brandRepository).existsByName(request.getName());
        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    void shouldGetBrandByIdSuccessfully() {
        UUID id = testBrand.getId();
        when(brandRepository.findById(id)).thenReturn(Optional.of(testBrand));

        Brand foundBrand = brandService.getBrandById(id);

        assertThat(foundBrand).isEqualTo(testBrand);
        verify(brandRepository).findById(id);
    }

    @Test
    void shouldThrowExceptionWhenBrandNotFoundById() {
        UUID id = UUID.randomUUID();
        when(brandRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brandService.getBrandById(id))
                .isInstanceOf(BrandNotFoundException.class)
                .hasMessageContaining("Brand not found with id");

        verify(brandRepository).findById(id);
    }

    @Test
    void shouldGetBrandByNameSuccessfully() {
        String name = testBrand.getName();
        when(brandRepository.findByName(name)).thenReturn(Optional.of(testBrand));

        Brand foundBrand = brandService.getBrandByName(name);

        assertThat(foundBrand).isEqualTo(testBrand);
        verify(brandRepository).findByName(name);
    }

    @Test
    void shouldThrowExceptionWhenBrandNotFoundByName() {
        String name = "Unknown Brand";
        when(brandRepository.findByName(name)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brandService.getBrandByName(name))
                .isInstanceOf(BrandNotFoundException.class)
                .hasMessageContaining("Brand not found with name");

        verify(brandRepository).findByName(name);
    }

    @Test
    void shouldGetAllBrandsSuccessfully() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Brand> page = new PageImpl<>(List.of(testBrand));
        when(brandRepository.findAll(pageable)).thenReturn(page);

        Page<Brand> result = brandService.getAllBrands(pageable);

        assertThat(result.getContent()).containsExactly(testBrand);
        verify(brandRepository).findAll(pageable);
    }

    @Test
    void shouldGetActiveBrandsSuccessfully() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Brand> page = new PageImpl<>(List.of(testBrand));
        when(brandRepository.findByActiveTrue(pageable)).thenReturn(page);

        Page<Brand> result = brandService.getActiveBrands(pageable);

        assertThat(result.getContent()).containsExactly(testBrand);
        verify(brandRepository).findByActiveTrue(pageable);
    }

    @Test
    void shouldUpdateBrandSuccessfully() {
        UUID id = testBrand.getId();
        UpdateBrandRequest request = new UpdateBrandRequest("Updated Brand", "Updated description", true);

        when(brandRepository.findById(id)).thenReturn(Optional.of(testBrand));
        when(brandRepository.existsByName(request.getName())).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenReturn(testBrand);

        Brand updatedBrand = brandService.updateBrand(id, request);

        assertThat(updatedBrand.getName()).isEqualTo(request.getName());
        assertThat(updatedBrand.getDescription()).isEqualTo(request.getDescription());
        assertThat(updatedBrand.getActive()).isTrue();

        verify(brandRepository).findById(id);
        verify(brandRepository).existsByName(request.getName());
        verify(brandRepository).save(testBrand);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingBrandWithExistingName() {
        UUID id = testBrand.getId();
        UpdateBrandRequest request = new UpdateBrandRequest("Existing Brand", null, null);

        when(brandRepository.findById(id)).thenReturn(Optional.of(testBrand));
        when(brandRepository.existsByName(request.getName())).thenReturn(true);

        assertThatThrownBy(() -> brandService.updateBrand(id, request))
                .isInstanceOf(BrandAlreadyExistsException.class)
                .hasMessageContaining("Brand already exists with name");

        verify(brandRepository).findById(id);
        verify(brandRepository).existsByName(request.getName());
        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    void shouldUpdateBrandDescriptionWithoutNameCheck() {
        UUID id = testBrand.getId();
        UpdateBrandRequest request = new UpdateBrandRequest(null, "Only description", null);

        when(brandRepository.findById(id)).thenReturn(Optional.of(testBrand));
        when(brandRepository.save(any(Brand.class))).thenReturn(testBrand);

        brandService.updateBrand(id, request);

        verify(brandRepository, never()).existsByName(any());
        verify(brandRepository).save(testBrand);
        assertThat(testBrand.getDescription()).isEqualTo("Only description");
    }

    @Test
    void shouldDeactivateBrandOnDelete() {
        UUID id = testBrand.getId();
        when(brandRepository.findById(id)).thenReturn(Optional.of(testBrand));

        brandService.deleteBrand(id);

        ArgumentCaptor<Brand> captor = ArgumentCaptor.forClass(Brand.class);
        verify(brandRepository).save(captor.capture());
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void shouldActivateBrand() {
        UUID id = testBrand.getId();
        testBrand.setActive(false);
        when(brandRepository.findById(id)).thenReturn(Optional.of(testBrand));

        brandService.activateBrand(id);

        ArgumentCaptor<Brand> captor = ArgumentCaptor.forClass(Brand.class);
        verify(brandRepository).save(captor.capture());
        assertThat(captor.getValue().getActive()).isTrue();
    }
}
