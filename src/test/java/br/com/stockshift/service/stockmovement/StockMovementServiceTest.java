package br.com.stockshift.service.stockmovement;

import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.dto.stockmovement.CreateStockMovementItemRequest;
import br.com.stockshift.dto.stockmovement.CreateStockMovementRequest;
import br.com.stockshift.dto.stockmovement.StockMovementResponse;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.mapper.StockMovementMapper;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.InventoryLedgerRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.StockMovementRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

  @Mock
  private StockMovementRepository movementRepository;
  @Mock
  private BatchRepository batchRepository;
  @Mock
  private ProductRepository productRepository;
  @Mock
  private WarehouseRepository warehouseRepository;
  @Mock
  private InventoryLedgerRepository ledgerRepository;
  @Mock
  private StockMovementMapper mapper;
  @Mock
  private SecurityUtils securityUtils;
  @Mock
  private ProductService productService;

  @InjectMocks
  private StockMovementService service;

  private UUID tenantId;
  private UUID warehouseId;
  private UUID userId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    warehouseId = UUID.randomUUID();
    userId = UUID.randomUUID();
    TenantContext.setTenantId(tenantId);
  }

  @Test
  void shouldCreateInlineProductBeforeProcessingInMovement() {
    Product product = buildProduct("Inline Product");
    Warehouse warehouse = buildWarehouse();
    CreateStockMovementRequest request = buildInlineRequest(StockMovementType.PURCHASE_IN);
    stubInlineInMovement(product, warehouse);

    StockMovementResponse response = service.create(request);

    assertThat(response.getWarehouseId()).isEqualTo(warehouseId);
    verify(productService).createEntity(any(ProductRequest.class), any());
    verify(batchRepository, atLeastOnce()).save(argThat(batch -> {
      return batch.getCostPrice().equals(1290L) && batch.getSellingPrice().equals(2490L);
    }));
    verify(batchRepository, atLeastOnce()).save(argThat(batch -> batch.getOriginMovementItem() != null));
    verify(ledgerRepository).save(argThat(ledger -> ledger.getReferenceId() != null));
  }

  @Test
  void shouldRejectInlineProductForOutMovement() {
    CreateStockMovementRequest request = buildInlineRequest(StockMovementType.USAGE);

    when(securityUtils.getCurrentWarehouseId()).thenReturn(warehouseId);
    when(securityUtils.getCurrentUserId()).thenReturn(userId);
    when(movementRepository.findLatestCodeByTenantIdAndCodePrefix(any(), any()))
        .thenReturn(null);

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("New products can only be used in IN stock movements");

    verify(productService, never()).createEntity(any(ProductRequest.class), any());
    verify(movementRepository, never()).save(any(StockMovement.class));
  }

  private void stubInlineInMovement(Product product, Warehouse warehouse) {
    when(securityUtils.getCurrentWarehouseId()).thenReturn(warehouseId);
    when(securityUtils.getCurrentUserId()).thenReturn(userId);
    when(movementRepository.findLatestCodeByTenantIdAndCodePrefix(any(), any()))
        .thenReturn(null);
    when(productService.createEntity(any(ProductRequest.class), any())).thenReturn(product);
    when(warehouseRepository.findByTenantIdAndId(tenantId, warehouseId))
        .thenReturn(Optional.of(warehouse));
    when(batchRepository.findByProductIdAndWarehouseIdAndTenantId(
        product.getId(), warehouseId, tenantId)).thenReturn(List.of());
    when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
      StockMovement movement = invocation.getArgument(0);
      if (movement.getId() == null) {
        movement.setId(UUID.randomUUID());
      }
      return movement;
    });
    when(movementRepository.saveAndFlush(any(StockMovement.class))).thenAnswer(invocation -> {
      StockMovement movement = invocation.getArgument(0);
      movement.getItems().forEach(item -> item.setId(UUID.randomUUID()));
      return movement;
    });
    when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
    when(mapper.toResponse(any(StockMovement.class), any()))
        .thenReturn(StockMovementResponse.builder().warehouseId(warehouseId).build());
  }

  private CreateStockMovementRequest buildInlineRequest(StockMovementType type) {
    ProductRequest newProduct = ProductRequest.builder()
        .name("Inline Product")
        .active(true)
        .isKit(false)
        .hasExpiration(false)
        .build();
    CreateStockMovementItemRequest item = CreateStockMovementItemRequest.builder()
        .newProduct(newProduct)
        .quantity(new BigDecimal("3"))
        .costPrice(1290L)
        .sellingPrice(2490L)
        .build();
    return CreateStockMovementRequest.builder()
        .type(type)
        .items(List.of(item))
        .build();
  }

  private Product buildProduct(String name) {
    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setTenantId(tenantId);
    product.setName(name);
    product.setSku("SKU-001");
    return product;
  }

  private Warehouse buildWarehouse() {
    Warehouse warehouse = new Warehouse();
    warehouse.setId(warehouseId);
    warehouse.setTenantId(tenantId);
    warehouse.setName("Main");
    return warehouse;
  }
}
