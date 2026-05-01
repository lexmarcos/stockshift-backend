package br.com.stockshift.service.stockmovement;

import br.com.stockshift.dto.product.ProductRequest;
import br.com.stockshift.dto.stockmovement.CreateStockMovementItemRequest;
import br.com.stockshift.dto.stockmovement.CreateStockMovementRequest;
import br.com.stockshift.dto.stockmovement.StockMovementResponse;
import br.com.stockshift.dto.stockmovement.WarehouseMovementSummaryResponse;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.InsufficientStockException;
import br.com.stockshift.mapper.StockMovementMapper;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.entity.StockMovementItem;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.model.enums.MovementDirection;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.InventoryLedgerRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.StockMovementItemRepository;
import br.com.stockshift.repository.StockMovementRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.ProductService;
import br.com.stockshift.service.audit.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockMovementServiceTest {

  @Mock
  private StockMovementRepository movementRepository;
  @Mock
  private StockMovementItemRepository movementItemRepository;
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
  @Mock
  private AuditService auditService;

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

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldCreateInlineProductBeforeProcessingInMovement() {
    Product product = buildProduct("Inline Product");
    Warehouse warehouse = buildWarehouse();
    CreateStockMovementRequest request = buildInlineRequest(StockMovementType.PURCHASE_IN);
    stubInlineInMovement(product, warehouse);

    StockMovementResponse response = service.create(request);

    assertThat(response.getWarehouseId()).isEqualTo(warehouseId);
    verify(productService).createEntity(
        argThat(productRequest -> Boolean.TRUE.equals(productRequest.getHasExpiration())),
        any());
    verify(batchRepository, atLeastOnce()).save(argThat(batch -> {
      return batch.getCostPrice().equals(1290L)
          && batch.getSellingPrice().equals(2490L)
          && LocalDate.of(2026, 4, 1).equals(batch.getManufacturedDate())
          && LocalDate.of(2026, 12, 31).equals(batch.getExpirationDate());
    }));
    InOrder persistenceOrder = inOrder(batchRepository, movementItemRepository);
    persistenceOrder.verify(movementItemRepository).saveAndFlush(any(StockMovementItem.class));
    persistenceOrder.verify(batchRepository).save(argThat(batch -> batch.getOriginMovementItem() != null));
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

  @Test
  void shouldProcessOutMovementUsingFifoAndRejectInsufficientStock() {
    Product product = buildProduct("Existing");
    Batch first = buildBatch(product, new BigDecimal("2"));
    Batch second = buildBatch(product, new BigDecimal("5"));
    CreateStockMovementRequest request = buildExistingProductRequest(StockMovementType.USAGE,
        product.getId(), new BigDecimal("4"));
    stubExistingProductMovement(product);
    when(batchRepository.findByProductAndWarehouseForFifo(product.getId(), warehouseId, tenantId))
        .thenReturn(List.of(first, second));
    when(mapper.toResponse(any(StockMovement.class), any()))
        .thenReturn(StockMovementResponse.builder().warehouseId(warehouseId).build());

    StockMovementResponse response = service.create(request);

    assertThat(response.getWarehouseId()).isEqualTo(warehouseId);
    assertThat(first.getQuantity()).isEqualByComparingTo("0");
    assertThat(second.getQuantity()).isEqualByComparingTo("3");
    verify(ledgerRepository, atLeastOnce()).save(argThat(ledger ->
        ledger.getEntryType().name().equals("USAGE_OUT")
            && ledger.getQuantity().compareTo(BigDecimal.ZERO) < 0));

    Batch tooSmall = buildBatch(product, BigDecimal.ONE);
    when(batchRepository.findByProductAndWarehouseForFifo(product.getId(), warehouseId, tenantId))
        .thenReturn(List.of(tooSmall));
    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("Insufficient stock");
  }

  @Test
  void shouldAddInMovementToExistingBatchAndRejectManualTransferType() {
    Product product = buildProduct("Existing");
    Batch existing = buildBatch(product, new BigDecimal("3"));
    CreateStockMovementRequest inRequest = buildExistingProductRequest(StockMovementType.ADJUSTMENT_IN,
        product.getId(), new BigDecimal("4"));
    stubExistingProductMovement(product);
    when(warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)).thenReturn(Optional.of(buildWarehouse()));
    when(batchRepository.findByProductIdAndWarehouseIdAndTenantId(product.getId(), warehouseId, tenantId))
        .thenReturn(List.of(existing));
    when(mapper.toResponse(any(StockMovement.class), any()))
        .thenReturn(StockMovementResponse.builder().warehouseId(warehouseId).build());

    service.create(inRequest);

    assertThat(existing.getQuantity()).isEqualByComparingTo("7");
    verify(movementItemRepository, never()).saveAndFlush(any());

    CreateStockMovementRequest transferRequest = buildExistingProductRequest(StockMovementType.TRANSFER_OUT,
        product.getId(), BigDecimal.ONE);
    assertThatThrownBy(() -> service.create(transferRequest))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Transfer movements are created automatically");
  }

  @Test
  void shouldCreateTransferMovementAndReadMovementViews() {
    Product product = buildProduct("Existing");
    Warehouse warehouse = buildWarehouse();
    StockMovementItem item = StockMovementItem.builder()
        .productId(product.getId())
        .productName(product.getName())
        .productSku(product.getSku())
        .batchId(UUID.randomUUID())
        .batchCode("B1")
        .quantity(new BigDecimal("2"))
        .build();
    stubBaseMovementPersistence(warehouse);

    StockMovement created = service.createForTransfer(tenantId, warehouseId, userId,
        StockMovementType.TRANSFER_IN, UUID.randomUUID(), List.of(item), "notes");

    assertThat(created.getReferenceType()).isEqualTo("TRANSFER");
    assertThat(created.getItems()).hasSize(1);
    verify(auditService).record(any());

    when(movementRepository.findByTenantIdAndId(tenantId, created.getId())).thenReturn(Optional.of(created));
    when(mapper.toResponse(created, "Main"))
        .thenReturn(StockMovementResponse.builder().id(created.getId()).warehouseName("Main").build());
    assertThat(service.getById(created.getId()).getWarehouseName()).isEqualTo("Main");

    when(securityUtils.getCurrentWarehouseId()).thenReturn(warehouseId);
    when(movementRepository.findExtract(any(), any(), eq(product.getId()), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(created)));
    assertThat(service.list(null, product.getId(), null, null, null, PageRequest.of(0, 10)).getContent())
        .hasSize(1);
  }

  @Test
  void shouldSummarizeWarehouseMovementsByTypeAndDirection() {
    Warehouse warehouse = buildWarehouse();
    Product product = buildProduct("Existing");
    StockMovement inMovement = movement(StockMovementType.PURCHASE_IN, MovementDirection.IN);
    inMovement.addItem(item(product, new BigDecimal("5")));
    StockMovement outMovement = movement(StockMovementType.LOSS, MovementDirection.OUT);
    outMovement.addItem(item(product, new BigDecimal("2")));
    when(warehouseRepository.findAllByTenantId(tenantId)).thenReturn(List.of(warehouse));
    when(movementRepository.findForWarehouseSummary(eq(tenantId), eq(List.of(warehouseId)), any(), any()))
        .thenReturn(List.of(inMovement, outMovement));

    WarehouseMovementSummaryResponse response = service.getWarehouseSummary(
        LocalDateTime.now().minusDays(7), LocalDateTime.now());

    assertThat(response.getWarehouses()).hasSize(1);
    assertThat(response.getWarehouses().get(0).getTotalIn()).isEqualByComparingTo("5");
    assertThat(response.getWarehouses().get(0).getTotalOut()).isEqualByComparingTo("2");
    assertThat(response.getWarehouses().get(0).getMovementsByType()).hasSize(2);
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
    when(movementItemRepository.saveAndFlush(any(StockMovementItem.class))).thenAnswer(invocation -> {
      StockMovementItem item = invocation.getArgument(0);
      item.setId(UUID.randomUUID());
      return item;
    });
    when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
      StockMovement movement = invocation.getArgument(0);
      if (movement.getId() == null) {
        movement.setId(UUID.randomUUID());
      }
      return movement;
    });
    when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
    when(mapper.toResponse(any(StockMovement.class), any()))
        .thenReturn(StockMovementResponse.builder().warehouseId(warehouseId).build());
  }

  private void stubExistingProductMovement(Product product) {
    when(securityUtils.getCurrentWarehouseId()).thenReturn(warehouseId);
    when(securityUtils.getCurrentUserId()).thenReturn(userId);
    when(movementRepository.findLatestCodeByTenantIdAndCodePrefix(any(), any()))
        .thenReturn(null);
    when(productRepository.findByTenantIdAndId(tenantId, product.getId())).thenReturn(Optional.of(product));
    stubBaseMovementPersistence(buildWarehouse());
  }

  private void stubBaseMovementPersistence(Warehouse warehouse) {
    when(movementRepository.findLatestCodeByTenantIdAndCodePrefix(any(), any()))
        .thenReturn(null);
    when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
      StockMovement movement = invocation.getArgument(0);
      if (movement.getId() == null) {
        movement.setId(UUID.randomUUID());
      }
      return movement;
    });
    when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(ledgerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
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
        .manufacturedDate(LocalDate.of(2026, 4, 1))
        .expirationDate(LocalDate.of(2026, 12, 31))
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

  private CreateStockMovementRequest buildExistingProductRequest(
      StockMovementType type, UUID productId, BigDecimal quantity) {
    CreateStockMovementItemRequest item = CreateStockMovementItemRequest.builder()
        .productId(productId)
        .quantity(quantity)
        .costPrice(100L)
        .sellingPrice(200L)
        .build();
    return CreateStockMovementRequest.builder()
        .type(type)
        .items(List.of(item))
        .notes("notes")
        .build();
  }

  private Batch buildBatch(Product product, BigDecimal quantity) {
    Batch batch = Batch.builder()
        .product(product)
        .warehouse(buildWarehouse())
        .batchCode("B-" + UUID.randomUUID())
        .quantity(quantity)
        .transitQuantity(BigDecimal.ZERO)
        .build();
    batch.setId(UUID.randomUUID());
    batch.setTenantId(tenantId);
    return batch;
  }

  private StockMovement movement(StockMovementType type, MovementDirection direction) {
    StockMovement movement = StockMovement.builder()
        .code("MOV")
        .warehouseId(warehouseId)
        .type(type)
        .direction(direction)
        .createdByUserId(userId)
        .build();
    movement.setId(UUID.randomUUID());
    movement.setTenantId(tenantId);
    return movement;
  }

  private StockMovementItem item(Product product, BigDecimal quantity) {
    return StockMovementItem.builder()
        .productId(product.getId())
        .productName(product.getName())
        .productSku(product.getSku())
        .batchId(UUID.randomUUID())
        .batchCode("B")
        .quantity(quantity)
        .build();
  }

  private Warehouse buildWarehouse() {
    Warehouse warehouse = new Warehouse();
    warehouse.setId(warehouseId);
    warehouse.setTenantId(tenantId);
    warehouse.setName("Main");
    return warehouse;
  }
}
