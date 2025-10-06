package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.stock.CreateStockEventLineRequest;
import com.stockshift.backend.api.dto.stock.CreateStockEventRequest;
import com.stockshift.backend.domain.product.Product;
import com.stockshift.backend.domain.product.ProductVariant;
import com.stockshift.backend.domain.stock.StockEvent;
import com.stockshift.backend.domain.stock.StockEventLine;
import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockItem;
import com.stockshift.backend.domain.stock.StockReasonCode;
import com.stockshift.backend.domain.stock.exception.StockExpiredItemMovementBlockedException;
import com.stockshift.backend.domain.stock.exception.StockForbiddenException;
import com.stockshift.backend.domain.stock.exception.StockInsufficientQuantityException;
import com.stockshift.backend.domain.stock.exception.StockInvalidPayloadException;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
import com.stockshift.backend.domain.warehouse.Warehouse;
import com.stockshift.backend.infrastructure.repository.ProductVariantRepository;
import com.stockshift.backend.infrastructure.repository.StockEventRepository;
import com.stockshift.backend.infrastructure.repository.StockItemRepository;
import com.stockshift.backend.infrastructure.repository.UserRepository;
import com.stockshift.backend.infrastructure.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockEventServiceTest {

    @Mock
    private StockEventRepository stockEventRepository;

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StockEventService stockEventService;

    private User adminUser;
    private Warehouse warehouse;
    private ProductVariant variant;
    private Product product;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setActive(true);

        warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setActive(true);

        product = new Product();
        product.setId(UUID.randomUUID());
        product.setActive(true);
        product.setExpiryDate(LocalDate.now().plusDays(30));

        variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setActive(true);
        variant.setProduct(product);
    }

    @Test
    void shouldCreateInboundEventAndIncreaseStockProjection() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.PURCHASE,
                "purchase order",
                List.of(new CreateStockEventLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(stockItemRepository.findByWarehouseIdAndVariantIdIn(warehouse.getId(), List.of(variant.getId())))
                .thenReturn(List.of());
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(stockEventRepository.save(any(StockEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockEvent event = stockEventService.createStockEvent(request, null, adminUser);

        ArgumentCaptor<List<StockItem>> stockItemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(stockItemRepository).saveAll(stockItemsCaptor.capture());
        List<StockItem> persistedItems = stockItemsCaptor.getValue();
        assertThat(persistedItems).hasSize(1);
        assertThat(persistedItems.get(0).getQuantity()).isEqualTo(10L);

        assertThat(event.getLines()).hasSize(1);
        StockEventLine line = event.getLines().get(0);
        assertThat(line.getQuantity()).isEqualTo(10L);
        assertThat(line.getVariant()).isEqualTo(variant);

        verify(stockEventRepository).save(event);
    }

    @Test
    void shouldThrowWhenOutboundWouldMakeStockNegative() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.OUTBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.SALE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 5L))
        );

        StockItem existing = new StockItem();
        existing.setVariant(variant);
        existing.setWarehouse(warehouse);
        existing.setQuantity(3L);

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(stockItemRepository.findByWarehouseIdAndVariantIdIn(warehouse.getId(), List.of(variant.getId())))
                .thenReturn(List.of(existing));
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, adminUser))
                .isInstanceOf(StockInsufficientQuantityException.class);

        verify(stockItemRepository, never()).saveAll(any());
        verify(stockEventRepository, never()).save(any());
    }

    @Test
    void shouldRejectDuplicateVariantLines() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.PURCHASE,
                null,
                List.of(
                        new CreateStockEventLineRequest(variant.getId(), 5L),
                        new CreateStockEventLineRequest(variant.getId(), 3L)
                )
        );

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, adminUser))
                .isInstanceOf(StockInvalidPayloadException.class);

        verifyNoInteractions(productVariantRepository, stockItemRepository, stockEventRepository, userRepository);
    }

    @Test
    void shouldReturnExistingEventWhenIdempotencyKeyMatches() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouse.getId(),
                OffsetDateTime.now(ZoneOffset.UTC),
                StockReasonCode.PURCHASE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 4L))
        );

        StockEvent existingEvent = new StockEvent();
        existingEvent.setType(StockEventType.INBOUND);
        existingEvent.setWarehouse(warehouse);
        existingEvent.setReasonCode(StockReasonCode.PURCHASE);
        existingEvent.setNotes(null);
        StockEventLine line = new StockEventLine();
        line.setVariant(variant);
        line.setQuantity(4L);
        existingEvent.setLines(List.of(line));

        // Service validates warehouse first, then checks idempotency key
        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(stockEventRepository.findByIdempotencyKey("abc"))
                .thenReturn(Optional.of(existingEvent));

        StockEvent result = stockEventService.createStockEvent(request, "abc", adminUser);

        assertThat(result).isEqualTo(existingEvent);
        verifyNoInteractions(productVariantRepository, stockItemRepository, userRepository);
    }

    @Test
    void shouldBlockSellerFromInboundMovement() {
        User seller = new User();
        seller.setId(UUID.randomUUID());
        seller.setRole(UserRole.SELLER);
        seller.setActive(true);

        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.PURCHASE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 2L))
        );

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, seller))
                .isInstanceOf(StockForbiddenException.class);

        verifyNoInteractions(productVariantRepository, stockItemRepository, stockEventRepository, userRepository);
    }

    @Test
    void shouldBlockExpiredVariantWhenNotDiscarding() {
        product.setExpiryDate(LocalDate.now().minusDays(1));

        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouse.getId(),
                OffsetDateTime.now(ZoneOffset.UTC),
                StockReasonCode.PURCHASE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 1L))
        );

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(stockItemRepository.findByWarehouseIdAndVariantIdIn(warehouse.getId(), List.of(variant.getId())))
                .thenReturn(List.of());
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, adminUser))
                .isInstanceOf(StockExpiredItemMovementBlockedException.class);

        verify(stockItemRepository, never()).saveAll(any());
        verify(stockEventRepository, never()).save(any());
    }

    @Test
    void shouldAllowDiscardExpiredWithAdjustType() {
        product.setExpiryDate(LocalDate.now().minusDays(1));

        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.ADJUST,
                warehouse.getId(),
                OffsetDateTime.now(ZoneOffset.UTC),
                StockReasonCode.DISCARD_EXPIRED,
                "Discarding expired items",
                List.of(new CreateStockEventLineRequest(variant.getId(), -5L))
        );

        StockItem existing = new StockItem();
        existing.setVariant(variant);
        existing.setWarehouse(warehouse);
        existing.setQuantity(10L);

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(stockItemRepository.findByWarehouseIdAndVariantIdIn(warehouse.getId(), List.of(variant.getId())))
                .thenReturn(List.of(existing));
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(stockEventRepository.save(any(StockEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockEvent event = stockEventService.createStockEvent(request, null, adminUser);

        assertThat(event.getType()).isEqualTo(StockEventType.ADJUST);
        assertThat(event.getReasonCode()).isEqualTo(StockReasonCode.DISCARD_EXPIRED);
        assertThat(event.getLines()).hasSize(1);
        assertThat(event.getLines().get(0).getQuantity()).isEqualTo(-5L);
    }

    @Test
    void shouldCreateAdjustmentWithPositiveDelta() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.ADJUST,
                warehouse.getId(),
                null,
                StockReasonCode.COUNT_CORRECTION,
                "Inventory count correction",
                List.of(new CreateStockEventLineRequest(variant.getId(), 15L))
        );

        StockItem existing = new StockItem();
        existing.setVariant(variant);
        existing.setWarehouse(warehouse);
        existing.setQuantity(100L);

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(stockItemRepository.findByWarehouseIdAndVariantIdIn(warehouse.getId(), List.of(variant.getId())))
                .thenReturn(List.of(existing));
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(stockEventRepository.save(any(StockEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockEvent event = stockEventService.createStockEvent(request, null, adminUser);

        ArgumentCaptor<List<StockItem>> stockItemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(stockItemRepository).saveAll(stockItemsCaptor.capture());
        List<StockItem> persistedItems = stockItemsCaptor.getValue();
        assertThat(persistedItems).hasSize(1);
        assertThat(persistedItems.get(0).getQuantity()).isEqualTo(115L);

        assertThat(event.getLines().get(0).getQuantity()).isEqualTo(15L);
    }

    @Test
    void shouldCreateAdjustmentWithNegativeDelta() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.ADJUST,
                warehouse.getId(),
                null,
                StockReasonCode.DAMAGE,
                "Damaged items",
                List.of(new CreateStockEventLineRequest(variant.getId(), -20L))
        );

        StockItem existing = new StockItem();
        existing.setVariant(variant);
        existing.setWarehouse(warehouse);
        existing.setQuantity(50L);

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(stockItemRepository.findByWarehouseIdAndVariantIdIn(warehouse.getId(), List.of(variant.getId())))
                .thenReturn(List.of(existing));
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(stockEventRepository.save(any(StockEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockEvent event = stockEventService.createStockEvent(request, null, adminUser);

        ArgumentCaptor<List<StockItem>> stockItemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(stockItemRepository).saveAll(stockItemsCaptor.capture());
        List<StockItem> persistedItems = stockItemsCaptor.getValue();
        assertThat(persistedItems).hasSize(1);
        assertThat(persistedItems.get(0).getQuantity()).isEqualTo(30L);

        assertThat(event.getLines().get(0).getQuantity()).isEqualTo(-20L);
    }

    @Test
    void shouldRejectAdjustmentWithZeroQuantity() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.ADJUST,
                warehouse.getId(),
                null,
                StockReasonCode.OTHER,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 0L))
        );

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, adminUser))
                .isInstanceOf(StockInvalidPayloadException.class);
    }

    @Test
    void shouldRejectInboundWithZeroOrNegativeQuantity() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.PURCHASE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 0L))
        );

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, adminUser))
                .isInstanceOf(StockInvalidPayloadException.class);
    }

    @Test
    void shouldRejectOutboundWithZeroOrNegativeQuantity() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.OUTBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.SALE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), -5L))
        );

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, adminUser))
                .isInstanceOf(StockInvalidPayloadException.class);
    }

    @Test
    void shouldAllowSellerToCreateOutboundEvent() {
        User seller = new User();
        seller.setId(UUID.randomUUID());
        seller.setRole(UserRole.SELLER);
        seller.setActive(true);

        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.OUTBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.SALE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 5L))
        );

        StockItem existing = new StockItem();
        existing.setVariant(variant);
        existing.setWarehouse(warehouse);
        existing.setQuantity(10L);

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(stockItemRepository.findByWarehouseIdAndVariantIdIn(warehouse.getId(), List.of(variant.getId())))
                .thenReturn(List.of(existing));
        when(userRepository.findById(seller.getId())).thenReturn(Optional.of(seller));
        when(stockEventRepository.save(any(StockEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockEvent event = stockEventService.createStockEvent(request, null, seller);

        assertThat(event.getType()).isEqualTo(StockEventType.OUTBOUND);
        assertThat(event.getCreatedBy()).isEqualTo(seller);
    }

    @Test
    void shouldAllowManagerToCreateAnyEventType() {
        User manager = new User();
        manager.setId(UUID.randomUUID());
        manager.setRole(UserRole.MANAGER);
        manager.setActive(true);

        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.ADJUST,
                warehouse.getId(),
                null,
                StockReasonCode.COUNT_CORRECTION,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(stockItemRepository.findByWarehouseIdAndVariantIdIn(warehouse.getId(), List.of(variant.getId())))
                .thenReturn(List.of());
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(stockEventRepository.save(any(StockEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockEvent event = stockEventService.createStockEvent(request, null, manager);

        assertThat(event.getType()).isEqualTo(StockEventType.ADJUST);
        assertThat(event.getCreatedBy()).isEqualTo(manager);
    }

    @Test
    void shouldThrowWhenUserIsNull() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.PURCHASE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 10L))
        );

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, null))
                .isInstanceOf(StockForbiddenException.class);

        verifyNoInteractions(warehouseRepository, productVariantRepository, stockItemRepository,
                            stockEventRepository, userRepository);
    }

    @Test
    void shouldThrowWhenUserRoleIsNull() {
        User userWithoutRole = new User();
        userWithoutRole.setId(UUID.randomUUID());
        userWithoutRole.setRole(null);

        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.PURCHASE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 10L))
        );

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, userWithoutRole))
                .isInstanceOf(StockForbiddenException.class);

        verifyNoInteractions(warehouseRepository, productVariantRepository, stockItemRepository,
                            stockEventRepository, userRepository);
    }

    @Test
    void shouldThrowWhenWarehouseNotFound() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                UUID.randomUUID(),
                null,
                StockReasonCode.PURCHASE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, adminUser))
                .isInstanceOf(com.stockshift.backend.domain.stock.exception.StockWarehouseNotFoundException.class);
    }

    @Test
    void shouldThrowWhenWarehouseIsInactive() {
        warehouse.setActive(false);

        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.PURCHASE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, adminUser))
                .isInstanceOf(com.stockshift.backend.domain.stock.exception.StockWarehouseInactiveException.class);
    }

    @Test
    void shouldThrowWhenVariantNotFound() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.PURCHASE,
                null,
                List.of(new CreateStockEventLineRequest(UUID.randomUUID(), 10L))
        );

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(productVariantRepository.findById(any())).thenReturn(Optional.empty());
        when(stockItemRepository.findByWarehouseIdAndVariantIdIn(any(), any())).thenReturn(List.of());
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, adminUser))
                .isInstanceOf(com.stockshift.backend.domain.stock.exception.StockVariantNotFoundException.class);
    }

    @Test
    void shouldThrowWhenVariantIsInactive() {
        variant.setActive(false);

        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.PURCHASE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(stockItemRepository.findByWarehouseIdAndVariantIdIn(warehouse.getId(), List.of(variant.getId())))
                .thenReturn(List.of());
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, adminUser))
                .isInstanceOf(com.stockshift.backend.domain.stock.exception.StockVariantInactiveException.class);
    }

    @Test
    void shouldThrowWhenProductIsInactive() {
        product.setActive(false);

        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                warehouse.getId(),
                null,
                StockReasonCode.PURCHASE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(stockItemRepository.findByWarehouseIdAndVariantIdIn(warehouse.getId(), List.of(variant.getId())))
                .thenReturn(List.of());
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, null, adminUser))
                .isInstanceOf(StockInvalidPayloadException.class)
                .hasMessageContaining("product-inactive");
    }

    @Test
    void shouldThrowIdempotencyConflictWhenEventTypesDiffer() {
        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.OUTBOUND,
                warehouse.getId(),
                OffsetDateTime.now(ZoneOffset.UTC),
                StockReasonCode.SALE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 5L))
        );

        StockEvent existingEvent = new StockEvent();
        existingEvent.setType(StockEventType.INBOUND);
        existingEvent.setWarehouse(warehouse);
        existingEvent.setReasonCode(StockReasonCode.SALE);
        existingEvent.setNotes(null);
        StockEventLine line = new StockEventLine();
        line.setVariant(variant);
        line.setQuantity(-5L);
        existingEvent.setLines(List.of(line));

        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(stockEventRepository.findByIdempotencyKey("abc"))
                .thenReturn(Optional.of(existingEvent));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, "abc", adminUser))
                .isInstanceOf(com.stockshift.backend.domain.stock.exception.StockIdempotencyConflictException.class);
    }

    @Test
    void shouldThrowIdempotencyConflictWhenWarehousesDiffer() {
        Warehouse otherWarehouse = new Warehouse();
        otherWarehouse.setId(UUID.randomUUID());
        otherWarehouse.setActive(true);

        CreateStockEventRequest request = new CreateStockEventRequest(
                StockEventType.INBOUND,
                otherWarehouse.getId(),
                OffsetDateTime.now(ZoneOffset.UTC),
                StockReasonCode.PURCHASE,
                null,
                List.of(new CreateStockEventLineRequest(variant.getId(), 5L))
        );

        StockEvent existingEvent = new StockEvent();
        existingEvent.setType(StockEventType.INBOUND);
        existingEvent.setWarehouse(warehouse);
        existingEvent.setReasonCode(StockReasonCode.PURCHASE);
        existingEvent.setNotes(null);
        StockEventLine line = new StockEventLine();
        line.setVariant(variant);
        line.setQuantity(5L);
        existingEvent.setLines(List.of(line));

        when(warehouseRepository.findById(otherWarehouse.getId())).thenReturn(Optional.of(otherWarehouse));
        when(stockEventRepository.findByIdempotencyKey("abc"))
                .thenReturn(Optional.of(existingEvent));

        assertThatThrownBy(() -> stockEventService.createStockEvent(request, "abc", adminUser))
                .isInstanceOf(com.stockshift.backend.domain.stock.exception.StockIdempotencyConflictException.class);
    }
}
