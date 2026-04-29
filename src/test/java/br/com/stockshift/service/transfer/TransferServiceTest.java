package br.com.stockshift.service.transfer;

import br.com.stockshift.dto.transfer.CancelTransferRequest;
import br.com.stockshift.dto.transfer.CreateTransferItemRequest;
import br.com.stockshift.dto.transfer.CreateTransferRequest;
import br.com.stockshift.dto.transfer.TransferResponse;
import br.com.stockshift.dto.transfer.UpdateTransferRequest;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.mapper.TransferMapper;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.InventoryLedger;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferItem;
import br.com.stockshift.model.entity.Warehouse;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.InventoryLedgerRepository;
import br.com.stockshift.repository.TransferItemRepository;
import br.com.stockshift.repository.TransferRepository;
import br.com.stockshift.repository.TransferValidationLogRepository;
import br.com.stockshift.repository.WarehouseRepository;
import br.com.stockshift.security.SecurityUtils;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.service.audit.AuditService;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferServiceTest {

        @Mock
        private TransferRepository transferRepository;
        @Mock
        private TransferItemRepository transferItemRepository;
        @Mock
        private TransferValidationLogRepository validationLogRepository;
        @Mock
        private BatchRepository batchRepository;
        @Mock
        private WarehouseRepository warehouseRepository;
        @Mock
        private InventoryLedgerRepository ledgerRepository;
        @Mock
        private TransferMapper transferMapper;
        @Mock
        private TransferStateMachine stateMachine;
        @Mock
        private SecurityUtils securityUtils;
        @Mock
        private br.com.stockshift.service.stockmovement.StockMovementService stockMovementService;
        @Mock
        private AuditService auditService;

        @InjectMocks
        private TransferService transferService;

        @AfterEach
        void tearDown() {
                TenantContext.clear();
        }

        @Test
        void createShouldBuildTransferItemsGenerateCodeAndAudit() {
                UUID tenantId = UUID.randomUUID();
                UUID sourceWarehouseId = UUID.randomUUID();
                UUID destinationWarehouseId = UUID.randomUUID();
                UUID sourceBatchId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                TenantContext.setTenantId(tenantId);

                Warehouse sourceWarehouse = warehouse(sourceWarehouseId, "Source");
                Warehouse destinationWarehouse = warehouse(destinationWarehouseId, "Destination");
                Product product = product(tenantId);
                Batch batch = batch(sourceBatchId, tenantId, sourceWarehouse, product, new BigDecimal("10"));
                CreateTransferRequest request = CreateTransferRequest.builder()
                                .destinationWarehouseId(destinationWarehouseId)
                                .notes("notes")
                                .items(List.of(CreateTransferItemRequest.builder()
                                                .sourceBatchId(sourceBatchId)
                                                .quantity(new BigDecimal("4"))
                                                .build()))
                                .build();

                when(securityUtils.getCurrentWarehouseId()).thenReturn(sourceWarehouseId);
                when(securityUtils.getCurrentUserId()).thenReturn(userId);
                when(warehouseRepository.findByTenantIdAndId(tenantId, destinationWarehouseId))
                                .thenReturn(Optional.of(destinationWarehouse));
                when(warehouseRepository.findByTenantIdAndId(tenantId, sourceWarehouseId))
                                .thenReturn(Optional.of(sourceWarehouse));
                when(batchRepository.findByTenantIdAndId(tenantId, sourceBatchId)).thenReturn(Optional.of(batch));
                when(transferRepository.findLatestCodeByTenantIdAndCodePrefix(eq(tenantId), any()))
                                .thenReturn(null);
                when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
                        Transfer transfer = invocation.getArgument(0);
                        transfer.setId(UUID.randomUUID());
                        return transfer;
                });
                when(transferMapper.toResponse(any(Transfer.class), eq("Source"), eq("Destination")))
                                .thenReturn(TransferResponse.builder().status(TransferStatus.DRAFT).build());

                TransferResponse response = transferService.create(request);

                assertThat(response.getStatus()).isEqualTo(TransferStatus.DRAFT);
                verify(auditService).record(any());

                assertThatThrownBy(() -> transferService.create(CreateTransferRequest.builder()
                                .destinationWarehouseId(sourceWarehouseId)
                                .items(List.of())
                                .build()))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("must be different");
        }

        @Test
        void listGetAndUpdateShouldUseWarehouseScopeAndDraftRules() {
                UUID tenantId = UUID.randomUUID();
                UUID transferId = UUID.randomUUID();
                UUID sourceWarehouseId = UUID.randomUUID();
                UUID destinationWarehouseId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                TenantContext.setTenantId(tenantId);

                Transfer transfer = transfer(transferId, tenantId, sourceWarehouseId, destinationWarehouseId,
                                TransferStatus.DRAFT, userId);
                Warehouse source = warehouse(sourceWarehouseId, "Source");
                Warehouse destination = warehouse(destinationWarehouseId, "Destination");
                Batch batch = batch(UUID.randomUUID(), tenantId, source, product(tenantId), new BigDecimal("7"));

                when(securityUtils.getCurrentWarehouseId()).thenReturn(sourceWarehouseId);
                when(securityUtils.getCurrentUserId()).thenReturn(userId);
                when(transferRepository.findByTenantIdAndIdAndWarehouseScope(tenantId, transferId, sourceWarehouseId))
                                .thenReturn(Optional.of(transfer));
                when(transferRepository.findByTenantIdAndId(tenantId, transferId)).thenReturn(Optional.of(transfer));
                when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(source));
                when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destination));
                when(transferMapper.toResponse(any(Transfer.class), any(), any()))
                                .thenAnswer(invocation -> TransferResponse.builder()
                                                .id(((Transfer) invocation.getArgument(0)).getId())
                                                .status(((Transfer) invocation.getArgument(0)).getStatus())
                                                .sourceWarehouseName(invocation.getArgument(1))
                                                .destinationWarehouseName(invocation.getArgument(2))
                                                .build());
                when(transferRepository.findAllByTenantIdAndStatusAndWarehouseScope(
                                tenantId, TransferStatus.DRAFT, sourceWarehouseId, PageRequest.of(0, 10)))
                                .thenReturn(new PageImpl<>(List.of(transfer)));
                when(batchRepository.findByTenantIdAndId(tenantId, batch.getId())).thenReturn(Optional.of(batch));
                when(transferRepository.save(transfer)).thenReturn(transfer);

                assertThat(transferService.getById(transferId).getSourceWarehouseName()).isEqualTo("Source");
                assertThat(transferService.list(TransferStatus.DRAFT, null, null, PageRequest.of(0, 10)).getContent())
                                .hasSize(1);
                assertThat(transferService.update(transferId, UpdateTransferRequest.builder()
                                .notes("updated")
                                .items(List.of(CreateTransferItemRequest.builder()
                                                .sourceBatchId(batch.getId())
                                                .quantity(BigDecimal.ONE)
                                                .build()))
                                .build()).getStatus()).isEqualTo(TransferStatus.DRAFT);

                assertThatThrownBy(() -> transferService.list(null, destinationWarehouseId, null, PageRequest.of(0, 10)))
                                .isInstanceOf(ForbiddenException.class);

                transfer.setStatus(TransferStatus.IN_TRANSIT);
                assertThatThrownBy(() -> transferService.update(transferId, UpdateTransferRequest.builder().build()))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("DRAFT");
        }

        @Test
        void cancelShouldHandleDraftAndInTransitReversal() {
                UUID tenantId = UUID.randomUUID();
                UUID transferId = UUID.randomUUID();
                UUID sourceWarehouseId = UUID.randomUUID();
                UUID destinationWarehouseId = UUID.randomUUID();
                UUID sourceBatchId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                TenantContext.setTenantId(tenantId);
                Transfer transfer = transfer(transferId, tenantId, sourceWarehouseId, destinationWarehouseId,
                                TransferStatus.IN_TRANSIT, userId);
                TransferItem item = TransferItem.builder()
                                .sourceBatchId(sourceBatchId)
                                .productId(UUID.randomUUID())
                                .productName("Product")
                                .quantitySent(new BigDecimal("3"))
                                .quantityReceived(BigDecimal.ZERO)
                                .build();
                transfer.addItem(item);
                Batch batch = Batch.builder()
                                .batchCode("B1")
                                .quantity(BigDecimal.ZERO)
                                .transitQuantity(new BigDecimal("3"))
                                .build();
                batch.setId(sourceBatchId);

                when(securityUtils.getCurrentWarehouseId()).thenReturn(sourceWarehouseId);
                when(securityUtils.getCurrentUserId()).thenReturn(userId);
                when(transferRepository.findByTenantIdAndId(tenantId, transferId)).thenReturn(Optional.of(transfer));
                when(batchRepository.findByIdForUpdate(sourceBatchId)).thenReturn(Optional.of(batch));
                when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(transferRepository.save(transfer)).thenReturn(transfer);
                when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(warehouse(sourceWarehouseId, "Source")));
                when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(warehouse(destinationWarehouseId, "Destination")));
                when(transferMapper.toResponse(any(Transfer.class), any(), any()))
                                .thenReturn(TransferResponse.builder().status(TransferStatus.CANCELLED).build());

                assertThatThrownBy(() -> transferService.cancel(transferId, new CancelTransferRequest()))
                                .isInstanceOf(BadRequestException.class)
                                .hasMessageContaining("reason is required");

                TransferResponse response = transferService.cancel(transferId,
                                CancelTransferRequest.builder().reason("Damaged").build());

                assertThat(response.getStatus()).isEqualTo(TransferStatus.CANCELLED);
                assertThat(batch.getQuantity()).isEqualByComparingTo("3");
                assertThat(batch.getTransitQuantity()).isEqualByComparingTo("0");
                verify(ledgerRepository).save(any(InventoryLedger.class));
                verify(auditService, atLeastOnce()).record(any());
        }

        @Test
        void executeShouldPersistLedgerEntryWithTenantAndCreatedBy() {
                UUID tenantId = UUID.randomUUID();
                UUID transferId = UUID.randomUUID();
                UUID sourceWarehouseId = UUID.randomUUID();
                UUID destinationWarehouseId = UUID.randomUUID();
                UUID sourceBatchId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();

                TenantContext.setTenantId(tenantId);

                Transfer transfer = Transfer.builder()
                                .code("TRF-2026-0001")
                                .sourceWarehouseId(sourceWarehouseId)
                                .destinationWarehouseId(destinationWarehouseId)
                                .status(TransferStatus.DRAFT)
                                .createdByUserId(userId)
                                .build();
                transfer.setId(transferId);
                transfer.setTenantId(tenantId);

                TransferItem item = TransferItem.builder()
                                .sourceBatchId(sourceBatchId)
                                .productId(UUID.randomUUID())
                                .productName("Produto")
                                .quantitySent(new BigDecimal("5"))
                                .quantityReceived(BigDecimal.ZERO)
                                .build();
                transfer.addItem(item);

                Warehouse destinationWarehouse = new Warehouse();
                destinationWarehouse.setId(destinationWarehouseId);
                destinationWarehouse.setName("Natal");

                Warehouse sourceWarehouse = new Warehouse();
                sourceWarehouse.setId(sourceWarehouseId);
                sourceWarehouse.setName("Recife");

                Batch batch = Batch.builder()
                                .batchCode("BATCH-001")
                                .quantity(new BigDecimal("10"))
                                .transitQuantity(BigDecimal.ZERO)
                                .build();
                batch.setId(sourceBatchId);

                when(securityUtils.getCurrentWarehouseId()).thenReturn(sourceWarehouseId);
                when(securityUtils.getCurrentUserId()).thenReturn(userId);
                when(transferRepository.findByTenantIdAndId(tenantId, transferId)).thenReturn(Optional.of(transfer));
                when(warehouseRepository.findById(destinationWarehouseId))
                                .thenReturn(Optional.of(destinationWarehouse));
                when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
                when(batchRepository.findByIdForUpdate(sourceBatchId)).thenReturn(Optional.of(batch));
                when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(transferRepository.save(transfer)).thenReturn(transfer);
                when(transferMapper.toResponse(eq(transfer), eq("Recife"), eq("Natal")))
                                .thenReturn(TransferResponse.builder().id(transferId).status(TransferStatus.IN_TRANSIT)
                                                .build());

                transferService.execute(transferId);

                ArgumentCaptor<InventoryLedger> ledgerCaptor = ArgumentCaptor.forClass(InventoryLedger.class);
                verify(ledgerRepository).save(ledgerCaptor.capture());
                InventoryLedger savedLedger = ledgerCaptor.getValue();

                assertThat(savedLedger.getTenantId()).isEqualTo(tenantId);
                assertThat(savedLedger.getCreatedBy()).isEqualTo(userId);
        }

        private Transfer transfer(UUID id, UUID tenantId, UUID sourceWarehouseId, UUID destinationWarehouseId,
                        TransferStatus status, UUID userId) {
                Transfer transfer = Transfer.builder()
                                .code("TRF-2026-0001")
                                .sourceWarehouseId(sourceWarehouseId)
                                .destinationWarehouseId(destinationWarehouseId)
                                .status(status)
                                .createdByUserId(userId)
                                .build();
                transfer.setId(id);
                transfer.setTenantId(tenantId);
                return transfer;
        }

        private Warehouse warehouse(UUID id, String name) {
                Warehouse warehouse = new Warehouse();
                warehouse.setId(id);
                warehouse.setName(name);
                return warehouse;
        }

        private Product product(UUID tenantId) {
                Product product = new Product();
                product.setId(UUID.randomUUID());
                product.setTenantId(tenantId);
                product.setName("Product");
                product.setSku("SKU");
                product.setBarcode("BAR");
                return product;
        }

        private Batch batch(UUID id, UUID tenantId, Warehouse warehouse, Product product, BigDecimal quantity) {
                Batch batch = Batch.builder()
                                .batchCode("B1")
                                .warehouse(warehouse)
                                .product(product)
                                .quantity(quantity)
                                .transitQuantity(BigDecimal.ZERO)
                                .build();
                batch.setId(id);
                batch.setTenantId(tenantId);
                return batch;
        }
}
