package br.com.stockshift.service;

import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.dto.product.ProductResponse;
import br.com.stockshift.dto.warehouse.BatchDeletionResponse;
import br.com.stockshift.dto.warehouse.BatchRequest;
import br.com.stockshift.dto.warehouse.ProductBatchRequest;
import br.com.stockshift.dto.warehouse.ProductBatchResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.entity.StockMovementItem;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.audit.AuditService;
import br.com.stockshift.service.audit.AuditSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchServiceTest {

        @Mock
        private BatchRepository batchRepository;

        @Mock
        private ProductRepository productRepository;

        @Mock
        private WarehouseRepository warehouseRepository;

        @Mock
        private ProductService productService;

        @Mock
        private WarehouseAccessService warehouseAccessService;

        @Mock
        private br.com.stockshift.security.SecurityUtils securityUtils;

        @Mock
        private AuditService auditService;

        @Mock
        private AuditSnapshotService auditSnapshotService;

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
                                .quantity(new BigDecimal("100"))
                                .costPrice(1000L) // R$10.00 in cents
                                .sellingPrice(2000L) // R$20.00 in cents
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

                doNothing().when(warehouseAccessService).validateWarehouseAccess(any(UUID.class));
                when(auditSnapshotService.snapshot(any())).thenReturn(Map.of("id", "value"));
                when(auditSnapshotService.diff(any(), any())).thenReturn(List.of("quantity"));
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

        when(productService.create(any(ProductRequest.class), any()))
                .thenReturn(productResponse);

        Batch savedBatch = new Batch();
        savedBatch.setId(UUID.randomUUID());
        savedBatch.setProduct(product);
        savedBatch.setWarehouse(warehouse);
        savedBatch.setBatchCode("BATCH-001");
        savedBatch.setQuantity(new BigDecimal("100"));

        when(productRepository.findByTenantIdAndId(tenantId, productId))
                .thenReturn(Optional.of(product));

        when(batchRepository.save(any(Batch.class)))
                .thenReturn(savedBatch);

        // Act
        ProductBatchResponse response = batchService.createWithProduct(request, null);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getProduct()).isNotNull();
        assertThat(response.getBatch()).isNotNull();
        assertThat(response.getProduct().getId()).isEqualTo(productId);

        verify(productService).create(any(ProductRequest.class), any());
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
                assertThatThrownBy(() -> batchService.createWithProduct(request, null))
                                .isInstanceOf(BusinessException.class)
                                .hasMessageContaining("Product with SKU 'SKU-001' already exists");

                verify(productService, never()).create(any(), any());
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
                assertThatThrownBy(() -> batchService.createWithProduct(request, null))
                                .isInstanceOf(BusinessException.class)
                                .hasMessageContaining("Product with barcode '1234567890' already exists");

                verify(productService, never()).create(any(), any());
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
                assertThatThrownBy(() -> batchService.createWithProduct(request, null))
                                .isInstanceOf(BusinessException.class)
                                .hasMessageContaining("Warehouse is not active");

                verify(productService, never()).create(any(), any());
                verify(batchRepository, never()).save(any());
        }

        @Test
        void shouldThrowExceptionWhenExpirationDateMissingForProductWithExpiration() {
                // Arrange
                request.setHasExpiration(true);
                request.setExpirationDate(null);

                when(productRepository.findBySkuAndTenantId(any(), any()))
                                .thenReturn(Optional.empty());
                when(productRepository.findByBarcodeAndTenantId(any(), any()))
                                .thenReturn(Optional.empty());
                when(warehouseRepository.findByTenantIdAndId(tenantId, warehouseId))
                                .thenReturn(Optional.of(warehouse));
                when(batchRepository.findByTenantIdAndBatchCode(any(), any()))
                                .thenReturn(Optional.empty());

                // Act & Assert
                assertThatThrownBy(() -> batchService.createWithProduct(request, null))
                                .isInstanceOf(BusinessException.class)
                                .hasMessageContaining("Expiration date is required for products with expiration");
        }

        @Test
        void shouldThrowExceptionWhenExpirationDateBeforeManufacturedDate() {
                // Arrange
                request.setManufacturedDate(LocalDate.now());
                request.setExpirationDate(LocalDate.now().minusDays(1));

                when(productRepository.findBySkuAndTenantId(any(), any()))
                                .thenReturn(Optional.empty());
                when(productRepository.findByBarcodeAndTenantId(any(), any()))
                                .thenReturn(Optional.empty());
                when(warehouseRepository.findByTenantIdAndId(tenantId, warehouseId))
                                .thenReturn(Optional.of(warehouse));
                when(batchRepository.findByTenantIdAndBatchCode(any(), any()))
                                .thenReturn(Optional.empty());

                // Act & Assert
                assertThatThrownBy(() -> batchService.createWithProduct(request, null))
                                .isInstanceOf(BusinessException.class)
                                .hasMessageContaining("Expiration date must be after manufactured date");
        }

        @Test
        void shouldCreateBatchGenerateCodeAndMapOriginMovement() {
                BatchRequest batchRequest = BatchRequest.builder()
                                .productId(productId)
                                .warehouseId(warehouseId)
                                .quantity(new BigDecimal("5"))
                                .costPrice(100L)
                                .sellingPrice(200L)
                                .build();
                StockMovement movement = StockMovement.builder().code("MOV-1").build();
                movement.setId(UUID.randomUUID());
                StockMovementItem originItem = StockMovementItem.builder().build();
                originItem.setId(UUID.randomUUID());
                originItem.setStockMovement(movement);
                product.setHasExpiration(false);
                warehouse.setName("Main");
                when(batchRepository.findByTenantIdAndBatchCode(any(), any())).thenReturn(Optional.empty());
                when(productRepository.findByTenantIdAndId(tenantId, productId)).thenReturn(Optional.of(product));
                when(warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)).thenReturn(Optional.of(warehouse));
                when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> {
                        Batch batch = invocation.getArgument(0);
                        batch.setId(UUID.randomUUID());
                        batch.setOriginMovementItem(originItem);
                        return batch;
                });

                assertThat(batchService.create(batchRequest).getOriginStockMovementCode()).isEqualTo("MOV-1");
                verify(auditService).record(any());
        }

        @Test
        void shouldReadBatchesByWarehouseProductExpirationAndLowStock() {
                Batch batch = savedBatch();
                when(securityUtils.getCurrentWarehouseId()).thenReturn(warehouseId);
                when(batchRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId)).thenReturn(List.of(batch));
                when(batchRepository.findByTenantIdAndId(tenantId, batch.getId())).thenReturn(Optional.of(batch));
                when(batchRepository.findByProductIdAndWarehouseIdAndTenantId(productId, warehouseId, tenantId))
                                .thenReturn(List.of(batch));
                when(batchRepository.findExpiringBatches(any(), any(), eq(tenantId))).thenReturn(List.of(batch));
                when(batchRepository.findLowStock(10, tenantId)).thenReturn(List.of(batch));

                assertThat(batchService.findAll()).hasSize(1);
                assertThat(batchService.findById(batch.getId()).getBatchCode()).isEqualTo("BATCH-001");
                assertThat(batchService.findByWarehouse(warehouseId)).hasSize(1);
                assertThat(batchService.findByProduct(productId)).hasSize(1);
                assertThat(batchService.findByWarehouseAndProduct(warehouseId, productId)).hasSize(1);
                assertThat(batchService.findExpiringBatches(30)).hasSize(1);
                assertThat(batchService.findLowStock(10)).hasSize(1);

                when(securityUtils.getCurrentWarehouseId()).thenThrow(new UnauthorizedException("no warehouse"));
                when(warehouseAccessService.hasFullAccess()).thenReturn(true);
                when(batchRepository.findAllByTenantId(tenantId)).thenReturn(List.of(batch));
                when(batchRepository.findByProductIdAndTenantId(productId, tenantId)).thenReturn(List.of(batch));
                assertThat(batchService.findAll()).hasSize(1);
                assertThat(batchService.findByProduct(productId)).hasSize(1);
        }

        @Test
        void shouldUpdateDeleteDeleteAllAndSumAvailableQuantity() {
                Batch batch = savedBatch();
                Warehouse newWarehouse = new Warehouse();
                newWarehouse.setId(UUID.randomUUID());
                newWarehouse.setTenantId(tenantId);
                newWarehouse.setName("Other");
                Product newProduct = new Product();
                newProduct.setId(UUID.randomUUID());
                newProduct.setTenantId(tenantId);
                newProduct.setName("Other product");
                BatchRequest update = BatchRequest.builder()
                                .productId(newProduct.getId())
                                .warehouseId(newWarehouse.getId())
                                .batchCode("BATCH-002")
                                .quantity(new BigDecimal("7"))
                                .costPrice(110L)
                                .sellingPrice(220L)
                                .build();
                when(batchRepository.findByTenantIdAndId(tenantId, batch.getId())).thenReturn(Optional.of(batch));
                when(batchRepository.findByTenantIdAndBatchCode(tenantId, "BATCH-002")).thenReturn(Optional.empty());
                when(productRepository.findByTenantIdAndId(tenantId, newProduct.getId())).thenReturn(Optional.of(newProduct));
                when(productRepository.findByTenantIdAndId(tenantId, productId)).thenReturn(Optional.of(product));
                when(warehouseRepository.findByTenantIdAndId(tenantId, newWarehouse.getId())).thenReturn(Optional.of(newWarehouse));
                when(warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)).thenReturn(Optional.of(warehouse));
                when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(batchRepository.softDeleteByProductAndWarehouse(productId, warehouseId, tenantId)).thenReturn(2);
                when(batchRepository.findByProductIdAndWarehouseIdAndTenantId(productId, warehouseId, tenantId))
                                .thenReturn(List.of(batch, batch));

                assertThat(batchService.update(batch.getId(), update).getBatchCode()).isEqualTo("BATCH-002");
                batchService.delete(batch.getId());
                verify(batchRepository).delete(batch);
                BatchDeletionResponse deleted = batchService.deleteAllByProductAndWarehouse(warehouseId, productId);
                assertThat(deleted.deletedCount()).isEqualTo(2);
                assertThat(batchService.getAvailableQuantity(productId, warehouseId, tenantId))
                                .isEqualByComparingTo("14");
        }

        private Batch savedBatch() {
                warehouse.setName("Main");
                product.setHasExpiration(false);
                Batch batch = new Batch();
                batch.setId(UUID.randomUUID());
                batch.setTenantId(tenantId);
                batch.setProduct(product);
                batch.setWarehouse(warehouse);
                batch.setBatchCode("BATCH-001");
                batch.setQuantity(new BigDecimal("7"));
                batch.setCostPrice(100L);
                batch.setSellingPrice(200L);
                batch.setExpirationDate(LocalDate.now().plusDays(10));
                return batch;
        }
}
