package br.com.stockshift.service;

import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.dto.product.ProductResponse;
import br.com.stockshift.dto.warehouse.BatchRequest;
import br.com.stockshift.dto.warehouse.BatchResponse;
import br.com.stockshift.dto.warehouse.ProductBatchRequest;
import br.com.stockshift.dto.warehouse.ProductBatchResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private BatchService batchService;

    private UUID tenantId;
    private UUID warehouseId;
    private UUID productId;
    private ProductBatchRequest request;
    private Warehouse warehouse;
    private Product product;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        productId = UUID.randomUUID();

        TenantContext.setTenantId(tenantId);

        request = ProductBatchRequest.builder()
                .name("Test Product")
                .sku("SKU-001")
                .barcode("1234567890")
                .warehouseId(warehouseId)
                .batchCode("BATCH-001")
                .quantity(100)
                .costPrice(BigDecimal.valueOf(10.00))
                .sellingPrice(BigDecimal.valueOf(20.00))
                .build();

        warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setTenantId(tenantId);
        warehouse.setIsActive(true);

        product = new Product();
        product.setId(productId);
        product.setTenantId(tenantId);
        product.setName("Test Product");
        product.setSku("SKU-001");
    }

    @Test
    void shouldCreateProductAndBatchSuccessfully() {
        // Arrange
        when(productRepository.findBySkuAndTenantId("SKU-001", tenantId))
                .thenReturn(Optional.empty());
        when(productRepository.findByBarcodeAndTenantId("1234567890", tenantId))
                .thenReturn(Optional.empty());
        when(warehouseRepository.findByTenantIdAndId(tenantId, warehouseId))
                .thenReturn(Optional.of(warehouse));
        when(batchRepository.findByTenantIdAndBatchCode(tenantId, "BATCH-001"))
                .thenReturn(Optional.empty());

        ProductResponse productResponse = ProductResponse.builder()
                .id(productId)
                .name("Test Product")
                .sku("SKU-001")
                .build();

        when(productService.create(any(ProductRequest.class)))
                .thenReturn(productResponse);

        Batch savedBatch = new Batch();
        savedBatch.setId(UUID.randomUUID());
        savedBatch.setProduct(product);
        savedBatch.setWarehouse(warehouse);
        savedBatch.setBatchCode("BATCH-001");
        savedBatch.setQuantity(100);

        when(productRepository.findByTenantIdAndId(tenantId, productId))
                .thenReturn(Optional.of(product));

        when(batchRepository.save(any(Batch.class)))
                .thenReturn(savedBatch);

        // Act
        ProductBatchResponse response = batchService.createWithProduct(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getProduct()).isNotNull();
        assertThat(response.getBatch()).isNotNull();
        assertThat(response.getProduct().getId()).isEqualTo(productId);

        verify(productService).create(any(ProductRequest.class));
        verify(batchRepository).save(any(Batch.class));
    }

    @Test
    void shouldThrowExceptionWhenSkuAlreadyExists() {
        // Arrange
        Product existingProduct = new Product();
        existingProduct.setId(UUID.randomUUID());
        existingProduct.setSku("SKU-001");

        when(productRepository.findBySkuAndTenantId("SKU-001", tenantId))
                .thenReturn(Optional.of(existingProduct));

        // Act & Assert
        assertThatThrownBy(() -> batchService.createWithProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Product with SKU 'SKU-001' already exists");

        verify(productService, never()).create(any());
        verify(batchRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenBarcodeAlreadyExists() {
        // Arrange
        Product existingProduct = new Product();
        existingProduct.setId(UUID.randomUUID());
        existingProduct.setBarcode("1234567890");

        when(productRepository.findBySkuAndTenantId(any(), any()))
                .thenReturn(Optional.empty());
        when(productRepository.findByBarcodeAndTenantId("1234567890", tenantId))
                .thenReturn(Optional.of(existingProduct));

        // Act & Assert
        assertThatThrownBy(() -> batchService.createWithProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Product with barcode '1234567890' already exists");

        verify(productService, never()).create(any());
        verify(batchRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenWarehouseIsInactive() {
        // Arrange
        warehouse.setIsActive(false);

        when(productRepository.findBySkuAndTenantId(any(), any()))
                .thenReturn(Optional.empty());
        when(productRepository.findByBarcodeAndTenantId(any(), any()))
                .thenReturn(Optional.empty());
        when(warehouseRepository.findByTenantIdAndId(tenantId, warehouseId))
                .thenReturn(Optional.of(warehouse));

        // Act & Assert
        assertThatThrownBy(() -> batchService.createWithProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Warehouse is not active");

        verify(productService, never()).create(any());
        verify(batchRepository, never()).save(any());
    }
}
