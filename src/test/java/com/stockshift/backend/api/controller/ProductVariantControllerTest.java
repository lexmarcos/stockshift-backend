package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.variant.CreateProductVariantRequest;
import com.stockshift.backend.api.dto.variant.ProductVariantResponse;
import com.stockshift.backend.api.dto.variant.UpdateProductVariantRequest;
import com.stockshift.backend.api.mapper.ProductVariantMapper;
import com.stockshift.backend.application.service.ProductVariantService;
import com.stockshift.backend.domain.product.ProductVariant;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantControllerTest {

    @Mock
    private ProductVariantService variantService;

    @Mock
    private ProductVariantMapper variantMapper;

    @InjectMocks
    private ProductVariantController productVariantController;

    private ProductVariant variant;
    private ProductVariantResponse variantResponse;

    @BeforeEach
    void setUp() {
        variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setSku("SKU-1");

        variantResponse = new ProductVariantResponse();
        variantResponse.setId(variant.getId());
        variantResponse.setSku(variant.getSku());
    }

    @Test
    void createVariantShouldReturnCreatedResponse() {
        UUID productId = UUID.randomUUID();
        CreateProductVariantRequest request = new CreateProductVariantRequest();
        when(variantService.createVariant(productId, request)).thenReturn(variant);
        when(variantMapper.toResponse(variant)).thenReturn(variantResponse);

        ResponseEntity<ProductVariantResponse> response = productVariantController.createVariant(productId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(variantResponse);
    }

    @Test
    void getAllVariantsShouldDelegateBasedOnFlag() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<ProductVariant> page = new PageImpl<>(List.of(variant));
        when(variantService.getActiveVariants(pageable)).thenReturn(page);
        when(variantMapper.toResponse(variant)).thenReturn(variantResponse);

        ResponseEntity<Page<ProductVariantResponse>> response = productVariantController.getAllVariants(true, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(variantResponse);
        verify(variantService).getActiveVariants(pageable);
    }

    @Test
    void getVariantBySkuShouldReturnMappedResponse() {
        when(variantService.getVariantBySku("SKU-1")).thenReturn(Optional.of(variant));
        when(variantMapper.toResponse(variant)).thenReturn(variantResponse);

        ResponseEntity<ProductVariantResponse> response = productVariantController.getVariantBySku("SKU-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(variantResponse);
    }

    @Test
    void activateVariantShouldReturnMappedResponse() {
        UUID id = UUID.randomUUID();
        when(variantService.getVariantById(id)).thenReturn(variant);
        when(variantMapper.toResponse(variant)).thenReturn(variantResponse);

        ResponseEntity<ProductVariantResponse> response = productVariantController.activateVariant(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(variantResponse);
        verify(variantService).activateVariant(id);
    }
}
