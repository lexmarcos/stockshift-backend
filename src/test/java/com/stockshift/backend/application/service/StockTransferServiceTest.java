package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.transfer.CreateTransferLineRequest;
import com.stockshift.backend.api.dto.transfer.CreateTransferRequest;
import com.stockshift.backend.domain.product.Product;
import com.stockshift.backend.domain.product.ProductVariant;
import com.stockshift.backend.domain.stock.StockEvent;
import com.stockshift.backend.domain.stock.StockEventType;
import com.stockshift.backend.domain.stock.StockTransfer;
import com.stockshift.backend.domain.stock.TransferStatus;
import com.stockshift.backend.domain.stock.exception.SameWarehouseTransferException;
import com.stockshift.backend.domain.stock.exception.StockForbiddenException;
import com.stockshift.backend.domain.stock.exception.StockInvalidPayloadException;
import com.stockshift.backend.domain.stock.exception.StockVariantInactiveException;
import com.stockshift.backend.domain.stock.exception.StockVariantNotFoundException;
import com.stockshift.backend.domain.stock.exception.StockWarehouseInactiveException;
import com.stockshift.backend.domain.stock.exception.StockWarehouseNotFoundException;
import com.stockshift.backend.domain.stock.exception.TransferIdempotencyConflictException;
import com.stockshift.backend.domain.stock.exception.TransferNotFoundException;
import com.stockshift.backend.domain.stock.exception.TransferNotDraftException;
import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
import com.stockshift.backend.domain.warehouse.Warehouse;
import com.stockshift.backend.infrastructure.repository.ProductVariantRepository;
import com.stockshift.backend.infrastructure.repository.StockTransferRepository;
import com.stockshift.backend.infrastructure.repository.UserRepository;
import com.stockshift.backend.infrastructure.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTransferServiceTest {

    @Mock
    private StockTransferRepository transferRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StockEventService stockEventService;

    @InjectMocks
    private StockTransferService transferService;

    private User adminUser;
    private User managerUser;
    private User sellerUser;
    private Warehouse originWarehouse;
    private Warehouse destinationWarehouse;
    private ProductVariant variant;
    private Product product;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setActive(true);

        managerUser = new User();
        managerUser.setId(UUID.randomUUID());
        managerUser.setRole(UserRole.MANAGER);
        managerUser.setActive(true);

        sellerUser = new User();
        sellerUser.setId(UUID.randomUUID());
        sellerUser.setRole(UserRole.SELLER);
        sellerUser.setActive(true);

        originWarehouse = new Warehouse();
        originWarehouse.setId(UUID.randomUUID());
        originWarehouse.setCode("WH-ORIGIN");
        originWarehouse.setActive(true);

        destinationWarehouse = new Warehouse();
        destinationWarehouse.setId(UUID.randomUUID());
        destinationWarehouse.setCode("WH-DEST");
        destinationWarehouse.setActive(true);

        product = new Product();
        product.setId(UUID.randomUUID());
        product.setActive(true);

        variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setSku("SKU-001");
        variant.setActive(true);
        variant.setProduct(product);
    }

    // ===== CREATE DRAFT TESTS =====

    @Test
    void shouldCreateDraftTransferSuccessfully() {
        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationWarehouse.getId(),
                OffsetDateTime.now(ZoneOffset.UTC),
                "Test transfer",
                List.of(new CreateTransferLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(originWarehouse.getId())).thenReturn(Optional.of(originWarehouse));
        when(warehouseRepository.findById(destinationWarehouse.getId())).thenReturn(Optional.of(destinationWarehouse));
        when(variantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(transferRepository.save(any(StockTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockTransfer transfer = transferService.createDraft(request, adminUser);

        assertThat(transfer).isNotNull();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.DRAFT);
        assertThat(transfer.getOriginWarehouse()).isEqualTo(originWarehouse);
        assertThat(transfer.getDestinationWarehouse()).isEqualTo(destinationWarehouse);
        assertThat(transfer.getLines()).hasSize(1);
        assertThat(transfer.getLines().get(0).getQuantity()).isEqualTo(10L);
        assertThat(transfer.getCreatedBy()).isEqualTo(adminUser);
        verify(transferRepository).save(any(StockTransfer.class));
    }

    @Test
    void shouldRejectSameOriginAndDestination() {
        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                originWarehouse.getId(), // Same as origin
                null,
                null,
                List.of(new CreateTransferLineRequest(variant.getId(), 10L))
        );

        assertThatThrownBy(() -> transferService.createDraft(request, adminUser))
                .isInstanceOf(SameWarehouseTransferException.class);

        verify(transferRepository, never()).save(any());
    }

    @Test
    void shouldRejectWhenOriginWarehouseNotFound() {
        CreateTransferRequest request = new CreateTransferRequest(
                UUID.randomUUID(),
                destinationWarehouse.getId(),
                null,
                null,
                List.of(new CreateTransferLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.createDraft(request, adminUser))
                .isInstanceOf(StockWarehouseNotFoundException.class);
    }

    @Test
    void shouldRejectWhenDestinationWarehouseNotFound() {
        UUID destinationId = UUID.randomUUID();
        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationId,
                null,
                null,
                List.of(new CreateTransferLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(originWarehouse.getId())).thenReturn(Optional.of(originWarehouse));
        when(warehouseRepository.findById(destinationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.createDraft(request, adminUser))
                .isInstanceOf(StockWarehouseNotFoundException.class);
    }

    @Test
    void shouldRejectWhenOriginWarehouseInactive() {
        originWarehouse.setActive(false);

        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationWarehouse.getId(),
                null,
                null,
                List.of(new CreateTransferLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(originWarehouse.getId())).thenReturn(Optional.of(originWarehouse));
        when(warehouseRepository.findById(destinationWarehouse.getId())).thenReturn(Optional.of(destinationWarehouse));

        assertThatThrownBy(() -> transferService.createDraft(request, adminUser))
                .isInstanceOf(StockWarehouseInactiveException.class);
    }

    @Test
    void shouldRejectWhenDestinationWarehouseInactive() {
        destinationWarehouse.setActive(false);

        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationWarehouse.getId(),
                null,
                null,
                List.of(new CreateTransferLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(originWarehouse.getId())).thenReturn(Optional.of(originWarehouse));
        when(warehouseRepository.findById(destinationWarehouse.getId())).thenReturn(Optional.of(destinationWarehouse));

        assertThatThrownBy(() -> transferService.createDraft(request, adminUser))
                .isInstanceOf(StockWarehouseInactiveException.class);
    }

    @Test
    void shouldRejectWhenVariantNotFound() {
        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationWarehouse.getId(),
                null,
                null,
                List.of(new CreateTransferLineRequest(UUID.randomUUID(), 10L))
        );

        when(warehouseRepository.findById(originWarehouse.getId())).thenReturn(Optional.of(originWarehouse));
        when(warehouseRepository.findById(destinationWarehouse.getId())).thenReturn(Optional.of(destinationWarehouse));
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(variantRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.createDraft(request, adminUser))
                .isInstanceOf(StockVariantNotFoundException.class);
    }

    @Test
    void shouldRejectWhenVariantInactive() {
        variant.setActive(false);

        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationWarehouse.getId(),
                null,
                null,
                List.of(new CreateTransferLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(originWarehouse.getId())).thenReturn(Optional.of(originWarehouse));
        when(warehouseRepository.findById(destinationWarehouse.getId())).thenReturn(Optional.of(destinationWarehouse));
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(variantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> transferService.createDraft(request, adminUser))
                .isInstanceOf(StockVariantInactiveException.class);
    }

    @Test
    void shouldRejectWhenProductInactive() {
        product.setActive(false);

        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationWarehouse.getId(),
                null,
                null,
                List.of(new CreateTransferLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(originWarehouse.getId())).thenReturn(Optional.of(originWarehouse));
        when(warehouseRepository.findById(destinationWarehouse.getId())).thenReturn(Optional.of(destinationWarehouse));
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(variantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> transferService.createDraft(request, adminUser))
                .isInstanceOf(StockInvalidPayloadException.class)
                .hasMessageContaining("product-inactive");
    }

    @Test
    void shouldRejectDuplicateVariantLines() {
        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationWarehouse.getId(),
                null,
                null,
                List.of(
                        new CreateTransferLineRequest(variant.getId(), 10L),
                        new CreateTransferLineRequest(variant.getId(), 5L)
                )
        );

        when(warehouseRepository.findById(originWarehouse.getId())).thenReturn(Optional.of(originWarehouse));
        when(warehouseRepository.findById(destinationWarehouse.getId())).thenReturn(Optional.of(destinationWarehouse));

        assertThatThrownBy(() -> transferService.createDraft(request, adminUser))
                .isInstanceOf(StockInvalidPayloadException.class)
                .hasMessageContaining("duplicate-variant-line");
    }

    @Test
    void shouldRejectEmptyLines() {
        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationWarehouse.getId(),
                null,
                null,
                List.of()
        );

        when(warehouseRepository.findById(originWarehouse.getId())).thenReturn(Optional.of(originWarehouse));
        when(warehouseRepository.findById(destinationWarehouse.getId())).thenReturn(Optional.of(destinationWarehouse));

        assertThatThrownBy(() -> transferService.createDraft(request, adminUser))
                .isInstanceOf(StockInvalidPayloadException.class)
                .hasMessageContaining("empty-lines");
    }

    @Test
    void shouldRejectWhenUserIsNull() {
        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationWarehouse.getId(),
                null,
                null,
                List.of(new CreateTransferLineRequest(variant.getId(), 10L))
        );

        assertThatThrownBy(() -> transferService.createDraft(request, null))
                .isInstanceOf(StockForbiddenException.class);
    }

    @Test
    void shouldRejectWhenUserRoleIsNull() {
        User userWithoutRole = new User();
        userWithoutRole.setId(UUID.randomUUID());
        userWithoutRole.setRole(null);

        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationWarehouse.getId(),
                null,
                null,
                List.of(new CreateTransferLineRequest(variant.getId(), 10L))
        );

        assertThatThrownBy(() -> transferService.createDraft(request, userWithoutRole))
                .isInstanceOf(StockForbiddenException.class);
    }

    @Test
    void shouldAllowManagerToCreateDraft() {
        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationWarehouse.getId(),
                null,
                null,
                List.of(new CreateTransferLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(originWarehouse.getId())).thenReturn(Optional.of(originWarehouse));
        when(warehouseRepository.findById(destinationWarehouse.getId())).thenReturn(Optional.of(destinationWarehouse));
        when(variantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(userRepository.findById(managerUser.getId())).thenReturn(Optional.of(managerUser));
        when(transferRepository.save(any(StockTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockTransfer transfer = transferService.createDraft(request, managerUser);

        assertThat(transfer).isNotNull();
        assertThat(transfer.getCreatedBy()).isEqualTo(managerUser);
    }

    @Test
    void shouldRejectSellerFromCreatingDraft() {
        CreateTransferRequest request = new CreateTransferRequest(
                originWarehouse.getId(),
                destinationWarehouse.getId(),
                null,
                null,
                List.of(new CreateTransferLineRequest(variant.getId(), 10L))
        );

        when(warehouseRepository.findById(originWarehouse.getId())).thenReturn(Optional.of(originWarehouse));
        when(warehouseRepository.findById(destinationWarehouse.getId())).thenReturn(Optional.of(destinationWarehouse));

        assertThatThrownBy(() -> transferService.createDraft(request, sellerUser))
                .isInstanceOf(StockForbiddenException.class);
    }

    // ===== CONFIRM TRANSFER TESTS =====

    @Test
    void shouldConfirmTransferSuccessfully() {
        StockTransfer transfer = new StockTransfer();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus(TransferStatus.DRAFT);
        transfer.setOriginWarehouse(originWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);
        transfer.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        transfer.addLine(createTransferLine(variant, 10L));

        StockEvent outboundEvent = new StockEvent();
        outboundEvent.setId(UUID.randomUUID());
        outboundEvent.setType(StockEventType.OUTBOUND);

        StockEvent inboundEvent = new StockEvent();
        inboundEvent.setId(UUID.randomUUID());
        inboundEvent.setType(StockEventType.INBOUND);

        when(transferRepository.findWithDetailsById(transfer.getId())).thenReturn(Optional.of(transfer));
        when(stockEventService.createStockEvent(any(), any(), any()))
                .thenReturn(outboundEvent)
                .thenReturn(inboundEvent);
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(transferRepository.save(any(StockTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockTransfer confirmed = transferService.confirmTransfer(transfer.getId(), null, adminUser);

        assertThat(confirmed.getStatus()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedBy()).isEqualTo(adminUser);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
        assertThat(confirmed.getOutboundEvent()).isEqualTo(outboundEvent);
        assertThat(confirmed.getInboundEvent()).isEqualTo(inboundEvent);
    }

    @Test
    void shouldHandleIdempotencyOnConfirm() {
        UUID transferId = UUID.randomUUID();
        String idempotencyKey = "unique-key-123";

        StockTransfer existingTransfer = new StockTransfer();
        existingTransfer.setId(transferId);
        existingTransfer.setStatus(TransferStatus.CONFIRMED);
        existingTransfer.setIdempotencyKey(idempotencyKey);

        when(transferRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingTransfer));

        StockTransfer result = transferService.confirmTransfer(transferId, idempotencyKey, adminUser);

        assertThat(result).isEqualTo(existingTransfer);
        verify(stockEventService, never()).createStockEvent(any(), any(), any());
    }

    @Test
    void shouldRejectIdempotencyConflict() {
        UUID transferId = UUID.randomUUID();
        UUID otherTransferId = UUID.randomUUID();
        String idempotencyKey = "unique-key-123";

        StockTransfer existingTransfer = new StockTransfer();
        existingTransfer.setId(otherTransferId); // Different ID
        existingTransfer.setIdempotencyKey(idempotencyKey);

        when(transferRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingTransfer));

        assertThatThrownBy(() -> transferService.confirmTransfer(transferId, idempotencyKey, adminUser))
                .isInstanceOf(TransferIdempotencyConflictException.class);
    }

    @Test
    void shouldRejectConfirmWhenTransferNotFound() {
        UUID transferId = UUID.randomUUID();

        when(transferRepository.findWithDetailsById(transferId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.confirmTransfer(transferId, null, adminUser))
                .isInstanceOf(TransferNotFoundException.class);
    }

    @Test
    void shouldRejectConfirmWhenNotDraft() {
        StockTransfer transfer = new StockTransfer();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus(TransferStatus.CONFIRMED);

        when(transferRepository.findWithDetailsById(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> transferService.confirmTransfer(transfer.getId(), null, adminUser))
                .isInstanceOf(TransferNotDraftException.class);
    }

    @Test
    void shouldRejectSellerFromConfirming() {
        StockTransfer transfer = new StockTransfer();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus(TransferStatus.DRAFT);
        transfer.setOriginWarehouse(originWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);

        when(transferRepository.findWithDetailsById(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> transferService.confirmTransfer(transfer.getId(), null, sellerUser))
                .isInstanceOf(StockForbiddenException.class);
    }

    // ===== CANCEL DRAFT TESTS =====

    @Test
    void shouldCancelDraftSuccessfully() {
        StockTransfer transfer = new StockTransfer();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus(TransferStatus.DRAFT);
        transfer.setOriginWarehouse(originWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);

        when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(StockTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockTransfer canceled = transferService.cancelDraft(transfer.getId(), adminUser);

        assertThat(canceled.getStatus()).isEqualTo(TransferStatus.CANCELED);
        verify(transferRepository).save(transfer);
    }

    @Test
    void shouldRejectCancelWhenNotDraft() {
        StockTransfer transfer = new StockTransfer();
        transfer.setId(UUID.randomUUID());
        transfer.setStatus(TransferStatus.CONFIRMED);

        when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> transferService.cancelDraft(transfer.getId(), adminUser))
                .isInstanceOf(TransferNotDraftException.class);
    }

    @Test
    void shouldRejectCancelWhenTransferNotFound() {
        UUID transferId = UUID.randomUUID();

        when(transferRepository.findById(transferId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.cancelDraft(transferId, adminUser))
                .isInstanceOf(TransferNotFoundException.class);
    }

    // ===== GET TRANSFER TESTS =====

    @Test
    void shouldGetTransferSuccessfully() {
        StockTransfer transfer = new StockTransfer();
        transfer.setId(UUID.randomUUID());
        transfer.setOriginWarehouse(originWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);

        when(transferRepository.findWithDetailsById(transfer.getId())).thenReturn(Optional.of(transfer));

        StockTransfer result = transferService.getTransfer(transfer.getId(), adminUser);

        assertThat(result).isEqualTo(transfer);
    }

    @Test
    void shouldRejectGetWhenTransferNotFound() {
        UUID transferId = UUID.randomUUID();

        when(transferRepository.findWithDetailsById(transferId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.getTransfer(transferId, adminUser))
                .isInstanceOf(TransferNotFoundException.class);
    }

    @Test
    void shouldAllowSellerToGetTransfer() {
        StockTransfer transfer = new StockTransfer();
        transfer.setId(UUID.randomUUID());
        transfer.setOriginWarehouse(originWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);

        when(transferRepository.findWithDetailsById(transfer.getId())).thenReturn(Optional.of(transfer));

        StockTransfer result = transferService.getTransfer(transfer.getId(), sellerUser);

        assertThat(result).isEqualTo(transfer);
    }

    // Helper method
    private com.stockshift.backend.domain.stock.StockTransferLine createTransferLine(ProductVariant variant, Long quantity) {
        com.stockshift.backend.domain.stock.StockTransferLine line = new com.stockshift.backend.domain.stock.StockTransferLine();
        line.setVariant(variant);
        line.setQuantity(quantity);
        return line;
    }
}
