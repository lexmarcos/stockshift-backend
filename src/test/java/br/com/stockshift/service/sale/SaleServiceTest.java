package br.com.stockshift.service.sale;

import br.com.stockshift.dto.sale.CancelSaleRequest;
import br.com.stockshift.dto.sale.CreateSaleItemRequest;
import br.com.stockshift.dto.sale.CreateSaleRequest;
import br.com.stockshift.dto.sale.NextSaleCodeResponse;
import br.com.stockshift.dto.sale.SaleResponse;
import br.com.stockshift.dto.sale.SaleSummaryResponse;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.InsufficientStockException;
import br.com.stockshift.mapper.SaleMapper;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.InventoryLedger;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Sale;
import br.com.stockshift.model.entity.SaleItem;
import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.model.enums.LedgerEntryType;
import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.PaymentMode;
import br.com.stockshift.model.enums.SaleStatus;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.InventoryLedgerRepository;
import br.com.stockshift.repository.ProductRepository;
import br.com.stockshift.repository.SaleRepository;
import br.com.stockshift.repository.StockMovementRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.audit.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private InventoryLedgerRepository ledgerRepository;
    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SaleMapper mapper;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private InfinitePayCheckoutService infinitePayCheckoutService;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private SaleService saleService;

    private UUID tenantId;
    private UUID userId;
    private UUID warehouseId;
    private Warehouse warehouse;
    private Product product;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        warehouse = warehouse(warehouseId, "Main");
        product = product("Coffee");

        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(warehouseRepository.findByTenantIdAndId(tenantId, warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findByTenantIdAndId(tenantId, product.getId())).thenReturn(Optional.of(product));
        when(saleRepository.findLatestCodeByTenantIdAndCodePrefix(eq(tenantId), anyString())).thenReturn(null);
        when(movementRepository.findLatestCodeByTenantIdAndCodePrefix(eq(tenantId), anyString())).thenReturn(null);
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale sale = invocation.getArgument(0);
            if (sale.getId() == null) {
                sale.setId(UUID.randomUUID());
            }
            return sale;
        });
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            if (movement.getId() == null) {
                movement.setId(UUID.randomUUID());
            }
            return movement;
        });
        when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(InventoryLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Sale.class), anyString())).thenAnswer(invocation -> {
            Sale sale = invocation.getArgument(0);
            return SaleResponse.builder()
                    .id(sale.getId())
                    .code(sale.getCode())
                    .warehouseId(sale.getWarehouseId())
                    .warehouseName(invocation.getArgument(1))
                    .subtotal(sale.getSubtotal())
                    .discountAmount(sale.getDiscountAmount())
                    .total(sale.getTotal())
                    .status(sale.getStatus())
                    .paymentMode(sale.getPaymentMode())
                    .build();
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createShouldProcessManualBatchAndDiscount() {
        Batch batch = batch(product, warehouse, new BigDecimal("5"), 1000L);
        CreateSaleRequest request = saleRequest(PaymentMode.DIRECT,
                List.of(saleItem(product.getId(), batch.getId(), new BigDecimal("2"))));
        request.setDiscountPercentage(new BigDecimal("10"));
        when(batchRepository.findByIdForUpdate(batch.getId())).thenReturn(Optional.of(batch));

        SaleResponse response = saleService.create(request);

        assertThat(response.getSubtotal()).isEqualTo(2000L);
        assertThat(response.getDiscountAmount()).isEqualTo(200L);
        assertThat(response.getTotal()).isEqualTo(1800L);
        assertThat(response.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(batch.getQuantity()).isEqualByComparingTo("3");
        verify(ledgerRepository).save(any(InventoryLedger.class));
        verify(movementRepository).save(any(StockMovement.class));
        verify(auditService).record(any());
    }

    @Test
    void createShouldAllocateFifoAcrossBatches() {
        Batch olderExpiringBatch = batch(product, warehouse, new BigDecimal("2"), 700L);
        olderExpiringBatch.setExpirationDate(LocalDate.now().plusDays(1));
        Batch laterBatch = batch(product, warehouse, new BigDecimal("4"), 900L);
        laterBatch.setExpirationDate(LocalDate.now().plusDays(30));
        CreateSaleRequest request = saleRequest(PaymentMode.DIRECT,
                List.of(saleItem(product.getId(), null, new BigDecimal("5"))));
        when(batchRepository.findByProductAndWarehouseForFifo(product.getId(), warehouseId, tenantId))
                .thenReturn(List.of(laterBatch, olderExpiringBatch));

        SaleResponse response = saleService.create(request);

        assertThat(response.getSubtotal()).isEqualTo(4100L);
        assertThat(olderExpiringBatch.getQuantity()).isEqualByComparingTo("0");
        assertThat(laterBatch.getQuantity()).isEqualByComparingTo("1");
        verify(ledgerRepository, atLeastOnce()).save(any(InventoryLedger.class));
    }

    @Test
    void createShouldRejectManualBatchFromAnotherWarehouse() {
        Warehouse otherWarehouse = warehouse(UUID.randomUUID(), "Other");
        Batch batch = batch(product, otherWarehouse, new BigDecimal("5"), 1000L);
        CreateSaleRequest request = saleRequest(PaymentMode.DIRECT,
                List.of(saleItem(product.getId(), batch.getId(), BigDecimal.ONE)));
        when(batchRepository.findByIdForUpdate(batch.getId())).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> saleService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("specified warehouse");
    }

    @Test
    void createShouldRejectAutoAllocationWithInsufficientStock() {
        Batch batch = batch(product, warehouse, BigDecimal.ONE, 1000L);
        CreateSaleRequest request = saleRequest(PaymentMode.DIRECT,
                List.of(saleItem(product.getId(), null, new BigDecimal("2"))));
        when(batchRepository.findByProductAndWarehouseForFifo(product.getId(), warehouseId, tenantId))
                .thenReturn(List.of(batch));

        assertThatThrownBy(() -> saleService.create(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void createShouldGeneratePaymentLinkWhenTenantIsConfigured() {
        Batch batch = batch(product, warehouse, new BigDecimal("3"), 1500L);
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setInfinitepayHandle("my-store");
        CreateSaleRequest request = saleRequest(PaymentMode.LINK,
                List.of(saleItem(product.getId(), batch.getId(), BigDecimal.ONE)));
        InfinitePayCheckoutService.CheckoutLinkResponse link = new InfinitePayCheckoutService.CheckoutLinkResponse();
        link.setUrl("https://pay.example/link");
        link.setSlug("slug-123");

        when(batchRepository.findByIdForUpdate(batch.getId())).thenReturn(Optional.of(batch));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(infinitePayCheckoutService.generatePaymentLink(eq("my-store"), anyList(), anyString()))
                .thenReturn(link);

        SaleResponse response = saleService.create(request);

        assertThat(response.getPaymentLink()).isEqualTo("https://pay.example/link");
        verify(infinitePayCheckoutService).generatePaymentLink(eq("my-store"), anyList(), anyString());
    }

    @Test
    void createShouldRejectLinkSaleBelowInfinitePayMinimum() {
        Batch batch = batch(product, warehouse, new BigDecimal("3"), 50L);
        CreateSaleRequest request = saleRequest(PaymentMode.LINK,
                List.of(saleItem(product.getId(), batch.getId(), BigDecimal.ONE)));
        when(batchRepository.findByIdForUpdate(batch.getId())).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> saleService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mínimo");
        verify(infinitePayCheckoutService, never()).generatePaymentLink(anyString(), anyList(), anyString());
    }

    @Test
    void cancelShouldReturnStockAndPersistCancellationLedger() {
        Sale sale = sale(PaymentMode.DIRECT, SaleStatus.COMPLETED);
        Batch batch = batch(product, warehouse, new BigDecimal("3"), 1000L);
        SaleItem item = saleItem(batch, new BigDecimal("2"), 1000L);
        sale.addItem(item);
        when(saleRepository.findByTenantIdAndId(tenantId, sale.getId())).thenReturn(Optional.of(sale));
        when(batchRepository.findByIdForUpdate(batch.getId())).thenReturn(Optional.of(batch));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        SaleResponse response = saleService.cancel(sale.getId(),
                CancelSaleRequest.builder().cancellationReason("Customer asked").build());

        assertThat(response.getStatus()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(batch.getQuantity()).isEqualByComparingTo("5");
        ArgumentCaptor<InventoryLedger> ledgerCaptor = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getEntryType()).isEqualTo(LedgerEntryType.SALE_CANCEL_IN);
        verify(auditService).record(any());
    }

    @Test
    void confirmInfinitePayPaymentShouldUpdateOnlyPendingSales() {
        Sale pending = sale(PaymentMode.TAP, SaleStatus.PENDING);
        when(saleRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        saleService.confirmInfinitePayPayment(pending.getId(), "nsu", "aut", "visa");

        assertThat(pending.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(pending.getInfinitepayNsu()).isEqualTo("nsu");
        assertThat(pending.getInfinitepayAut()).isEqualTo("aut");
        assertThat(pending.getInfinitepayCardBrand()).isEqualTo("visa");

        Sale completed = sale(PaymentMode.TAP, SaleStatus.COMPLETED);
        when(saleRepository.findById(completed.getId())).thenReturn(Optional.of(completed));
        saleService.confirmInfinitePayPayment(completed.getId(), "ignored", "ignored", "ignored");

        assertThat(completed.getInfinitepayNsu()).isNull();
    }

    @Test
    void confirmInfinitePayWebhookShouldResolvePaymentMethodAndInstallments() {
        Sale sale = sale(PaymentMode.LINK, SaleStatus.PENDING);
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

        saleService.confirmInfinitePayWebhook(sale.getId(), "txn", "credit_card", "invoice", "receipt", 3);

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(sale.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(sale.getInstallments()).isEqualTo(3);
        assertThat(sale.getInfinitepayInvoiceSlug()).isEqualTo("invoice");
    }

    @Test
    void listAndGetNextCodeShouldMapRepositoryResults() {
        Sale sale = sale(PaymentMode.DIRECT, SaleStatus.COMPLETED);
        User user = new User();
        user.setId(userId);
        user.setFullName("Seller");
        when(saleRepository.findWithFilters(eq(tenantId), eq(warehouseId), eq(PaymentMethod.CASH),
                eq(SaleStatus.COMPLETED), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sale)));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mapper.toSummaryResponse(sale, "Main", "Seller"))
                .thenReturn(SaleSummaryResponse.builder().id(sale.getId()).createdByUserName("Seller").build());

        assertThat(saleService.list(warehouseId, PaymentMethod.CASH, SaleStatus.COMPLETED,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), PageRequest.of(0, 10)).getContent())
                .extracting(SaleSummaryResponse::getCreatedByUserName)
                .containsExactly("Seller");

        when(saleRepository.findLatestCodeByTenantIdAndCodePrefix(eq(tenantId), anyString()))
                .thenReturn("VND-" + LocalDate.now().getYear() + "-not-number");
        when(saleRepository.countByTenantIdAndCodePrefix(eq(tenantId), anyString())).thenReturn(8L);

        NextSaleCodeResponse nextCode = saleService.getNextCode();

        assertThat(nextCode.getCode()).endsWith("0009");
    }

    private CreateSaleRequest saleRequest(PaymentMode mode, List<CreateSaleItemRequest> items) {
        return CreateSaleRequest.builder()
                .warehouseId(warehouseId)
                .paymentMethod(PaymentMethod.CASH)
                .paymentMode(mode)
                .items(items)
                .build();
    }

    private CreateSaleItemRequest saleItem(UUID productId, UUID batchId, BigDecimal quantity) {
        return CreateSaleItemRequest.builder()
                .productId(productId)
                .batchId(batchId)
                .quantity(quantity)
                .build();
    }

    private Sale sale(PaymentMode paymentMode, SaleStatus status) {
        Sale sale = Sale.builder()
                .code("VND-2026-0001")
                .warehouseId(warehouseId)
                .paymentMethod(PaymentMethod.CASH)
                .paymentMode(paymentMode)
                .installments(1)
                .discountPercentage(BigDecimal.ZERO)
                .subtotal(1000L)
                .discountAmount(0L)
                .total(1000L)
                .status(status)
                .createdByUserId(userId)
                .build();
        sale.setId(UUID.randomUUID());
        sale.setTenantId(tenantId);
        return sale;
    }

    private SaleItem saleItem(Batch batch, BigDecimal quantity, long unitPrice) {
        SaleItem item = SaleItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .productSku(product.getSku())
                .batchId(batch.getId())
                .batchCode(batch.getBatchCode())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalPrice(unitPrice * quantity.longValue())
                .build();
        item.setId(UUID.randomUUID());
        return item;
    }

    private Batch batch(Product product, Warehouse warehouse, BigDecimal quantity, long sellingPrice) {
        Batch batch = Batch.builder()
                .product(product)
                .warehouse(warehouse)
                .batchCode("BATCH-" + UUID.randomUUID())
                .quantity(quantity)
                .sellingPrice(sellingPrice)
                .transitQuantity(BigDecimal.ZERO)
                .build();
        batch.setId(UUID.randomUUID());
        batch.setTenantId(product.getTenantId());
        batch.setCreatedAt(LocalDateTime.now());
        return batch;
    }

    private Product product(String name) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setName(name);
        p.setSku("SKU-" + name.toUpperCase());
        return p;
    }

    private Warehouse warehouse(UUID id, String name) {
        Warehouse wh = new Warehouse();
        wh.setId(id);
        wh.setTenantId(tenantId);
        wh.setName(name);
        wh.setCode(name.toUpperCase());
        wh.setCity("Recife");
        wh.setState("PE");
        wh.setIsActive(true);
        return wh;
    }
}
