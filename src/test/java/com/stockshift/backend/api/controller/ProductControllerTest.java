package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.product.CreateProductRequest;
import com.stockshift.backend.api.dto.product.ProductResponse;
import com.stockshift.backend.api.dto.product.UpdateProductRequest;
import com.stockshift.backend.api.mapper.ProductMapper;
import com.stockshift.backend.application.service.ProductService;
import com.stockshift.backend.domain.product.Product;
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
class ProductControllerTest {

    @Mock
    private ProductService productService;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductController productController;

    private Product product;
    private ProductResponse productResponse;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Laptop");

        productResponse = new ProductResponse();
        productResponse.setId(product.getId());
        productResponse.setName(product.getName());
    }

    @Test
    void createProductShouldReturnCreatedResponse() {
        CreateProductRequest request = new CreateProductRequest("Laptop", "desc", null, null, 1000L, null);
        when(productService.createProduct(request)).thenReturn(product);
        when(productMapper.toResponse(product)).thenReturn(productResponse);

        ResponseEntity<ProductResponse> response = productController.createProduct(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(productResponse);
    }

    @Test
    void getAllProductsShouldUseActiveServiceWhenFlagTrue() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productService.getActiveProducts(pageable)).thenReturn(page);
        when(productMapper.toResponse(product)).thenReturn(productResponse);

        ResponseEntity<Page<ProductResponse>> response = productController.getAllProducts(true, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(productResponse);
        verify(productService).getActiveProducts(pageable);
    }

    @Test
    void searchProductsShouldReturnMappedPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productService.searchProducts("lap", pageable)).thenReturn(page);
        when(productMapper.toResponse(product)).thenReturn(productResponse);

        ResponseEntity<Page<ProductResponse>> response = productController.searchProducts("lap", pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(productResponse);
    }

    @Test
    void activateProductShouldReturnUpdatedEntity() {
        UUID id = UUID.randomUUID();
        when(productService.getProductById(id)).thenReturn(product);
        when(productMapper.toResponse(product)).thenReturn(productResponse);

        ResponseEntity<ProductResponse> response = productController.activateProduct(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(productResponse);
        verify(productService).activateProduct(id);
    }
}
