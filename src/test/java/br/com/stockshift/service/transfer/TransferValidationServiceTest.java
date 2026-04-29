package br.com.stockshift.service.transfer;

import br.com.stockshift.dto.transfer.CompleteValidationResponse;
import br.com.stockshift.dto.transfer.DiscrepancyReportResponse;
import br.com.stockshift.dto.transfer.ScanBarcodeRequest;
import br.com.stockshift.dto.transfer.ScanBarcodeResponse;
import br.com.stockshift.dto.transfer.TransferResponse;
import br.com.stockshift.dto.transfer.ValidationLogResponse;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.ForbiddenException;
import br.com.stockshift.mapper.TransferMapper;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.Product;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferItem;
import br.com.stockshift.model.entity.TransferValidationLog;
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
import br.com.stockshift.service.stockmovement.StockMovementService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferValidationServiceTest {

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
    private StockMovementService stockMovementService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private TransferValidationService service;

    private UUID tenantId;
    private UUID userId;
    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private Map<UUID, Batch> savedBatches;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        sourceWarehouseId = UUID.randomUUID();
        destinationWarehouseId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        sourceWarehouse = warehouse(sourceWarehouseId, "Source");
        destinationWarehouse = warehouse(destinationWarehouseId, "Destination");
        savedBatches = new HashMap<>();

        when(securityUtils.getCurrentWarehouseId()).thenReturn(destinationWarehouseId);
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferItemRepository.save(any(TransferItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> {
            Batch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(UUID.randomUUID());
            }
            savedBatches.put(batch.getId(), batch);
            return batch;
        });
        when(batchRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(savedBatches.get(invocation.getArgument(0))));
        when(transferMapper.toResponse(any(Transfer.class), anyString(), anyString()))
                .thenAnswer(invocation -> TransferResponse.builder()
                        .id(((Transfer) invocation.getArgument(0)).getId())
                        .status(((Transfer) invocation.getArgument(0)).getStatus())
                        .sourceWarehouseName(invocation.getArgument(1))
                        .destinationWarehouseName(invocation.getArgument(2))
                        .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void startValidationShouldRequireDestinationWarehouseAndChangeStatus() {
        Transfer transfer = transfer(TransferStatus.IN_TRANSIT);
        when(transferRepository.findByTenantIdAndId(tenantId, transfer.getId())).thenReturn(Optional.of(transfer));

        TransferResponse response = service.startValidation(transfer.getId());

        assertThat(response.getStatus()).isEqualTo(TransferStatus.PENDING_VALIDATION);
        verify(stateMachine).validateTransition(TransferStatus.IN_TRANSIT, TransferStatus.PENDING_VALIDATION);
        verify(auditService).record(any());

        when(securityUtils.getCurrentWarehouseId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.startValidation(transfer.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void scanBarcodeShouldLogInvalidAndWarnWhenReceivedExceedsSent() {
        Transfer transfer = transfer(TransferStatus.PENDING_VALIDATION);
        TransferItem item = item("123", new BigDecimal("1"), BigDecimal.ONE);
        item.setId(UUID.randomUUID());
        transfer.addItem(item);
        when(transferRepository.findByTenantIdAndId(tenantId, transfer.getId())).thenReturn(Optional.of(transfer));

        when(transferItemRepository.findByTransferIdAndProductBarcode(transfer.getId(), "missing"))
                .thenReturn(Optional.empty());
        ScanBarcodeResponse invalid = service.scanBarcode(transfer.getId(), new ScanBarcodeRequest(" missing "));
        assertThat(invalid.isValid()).isFalse();
        assertThat(invalid.getMessage()).contains("does not belong");

        when(transferItemRepository.findByTransferIdAndProductBarcode(transfer.getId(), "123"))
                .thenReturn(Optional.of(item));
        ScanBarcodeResponse valid = service.scanBarcode(transfer.getId(), new ScanBarcodeRequest("123"));

        assertThat(valid.isValid()).isTrue();
        assertThat(valid.getWarning()).contains("exceeds");
        assertThat(item.getQuantityReceived()).isEqualByComparingTo("2");
        verify(validationLogRepository, atLeastOnce()).save(any(TransferValidationLog.class));
        verify(auditService, atLeastOnce()).record(any());
    }

    @Test
    void completeValidationShouldCreateDestinationBatchesLedgersDiscrepanciesAndMovement() {
        Transfer transfer = transfer(TransferStatus.PENDING_VALIDATION);
        TransferItem exact = item("EXACT", new BigDecimal("5"), new BigDecimal("5"));
        TransferItem shortage = item("SHORT", new BigDecimal("3"), BigDecimal.ONE);
        TransferItem overage = item("OVER", new BigDecimal("2"), new BigDecimal("4"));
        transfer.addItem(exact);
        transfer.addItem(shortage);
        transfer.addItem(overage);
        stubSourceBatch(exact, new BigDecimal("5"));
        stubSourceBatch(shortage, new BigDecimal("3"));
        stubSourceBatch(overage, new BigDecimal("2"));
        when(transferRepository.findByTenantIdAndId(tenantId, transfer.getId())).thenReturn(Optional.of(transfer));

        CompleteValidationResponse response = service.completeValidation(transfer.getId());

        assertThat(response.getStatus()).isEqualTo(TransferStatus.COMPLETED_WITH_DISCREPANCY);
        assertThat(response.getSummary().getItemsOk()).isEqualTo(1);
        assertThat(response.getSummary().getItemsWithDiscrepancy()).isEqualTo(2);
        assertThat(response.getDiscrepancies()).extracting("type")
                .contains(CompleteValidationResponse.DiscrepancyType.SHORTAGE,
                        CompleteValidationResponse.DiscrepancyType.OVERAGE);
        assertThat(transfer.getValidatedByUserId()).isEqualTo(userId);
        assertThat(exact.getDestinationBatchId()).isNotNull();
        verify(stockMovementService).createForTransfer(eq(tenantId), eq(destinationWarehouseId), eq(userId),
                any(), eq(transfer.getId()), any(), anyString());
        verify(auditService).record(any());
    }

    @Test
    void completeValidationShouldRejectWrongStatus() {
        Transfer transfer = transfer(TransferStatus.IN_TRANSIT);
        when(transferRepository.findByTenantIdAndId(tenantId, transfer.getId())).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.completeValidation(transfer.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING_VALIDATION");
    }

    @Test
    void discrepancyReportAndLogsShouldMapReadModels() {
        Transfer transfer = transfer(TransferStatus.COMPLETED_WITH_DISCREPANCY);
        transfer.setValidatedAt(Instant.now());
        TransferItem shortage = item("SHORT", new BigDecimal("5"), new BigDecimal("3"));
        TransferItem overage = item("OVER", BigDecimal.ONE, new BigDecimal("2"));
        transfer.addItem(shortage);
        transfer.addItem(overage);
        when(transferRepository.findByTenantIdAndIdAndWarehouseScope(tenantId, transfer.getId(), destinationWarehouseId))
                .thenReturn(Optional.of(transfer));
        TransferValidationLog log = TransferValidationLog.builder()
                .transferId(transfer.getId())
                .barcode("SHORT")
                .validatedByUserId(userId)
                .valid(true)
                .build();
        ValidationLogResponse logResponse = ValidationLogResponse.builder().barcode("SHORT").valid(true).build();
        when(validationLogRepository.findAllByTransferId(transfer.getId())).thenReturn(List.of(log));
        when(transferMapper.toValidationLogResponseList(List.of(log))).thenReturn(List.of(logResponse));

        DiscrepancyReportResponse report = service.getDiscrepancyReport(transfer.getId());
        List<ValidationLogResponse> logs = service.getValidationLogs(transfer.getId());

        assertThat(report.getDiscrepancies()).hasSize(2);
        assertThat(report.getTotalShortage()).isEqualByComparingTo("2");
        assertThat(report.getTotalOverage()).isEqualByComparingTo("1");
        assertThat(logs).extracting(ValidationLogResponse::getBarcode).containsExactly("SHORT");

        transfer.setStatus(TransferStatus.COMPLETED);
        assertThatThrownBy(() -> service.getDiscrepancyReport(transfer.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only available");
    }

    private void stubSourceBatch(TransferItem item, BigDecimal transitQuantity) {
        Batch batch = batch(item.getSourceBatchId(), item.getBatchCode(), item.getProductId(), transitQuantity);
        when(batchRepository.findByIdForUpdate(item.getSourceBatchId())).thenReturn(Optional.of(batch));
    }

    private Transfer transfer(TransferStatus status) {
        Transfer transfer = Transfer.builder()
                .code("TRF-2026-0001")
                .sourceWarehouseId(sourceWarehouseId)
                .destinationWarehouseId(destinationWarehouseId)
                .status(status)
                .createdByUserId(userId)
                .build();
        transfer.setId(UUID.randomUUID());
        transfer.setTenantId(tenantId);
        return transfer;
    }

    private TransferItem item(String barcode, BigDecimal sent, BigDecimal received) {
        TransferItem item = TransferItem.builder()
                .sourceBatchId(UUID.randomUUID())
                .batchCode("BATCH-" + barcode)
                .productId(UUID.randomUUID())
                .productBarcode(barcode)
                .productName("Product " + barcode)
                .productSku("SKU-" + barcode)
                .quantitySent(sent)
                .quantityReceived(received)
                .build();
        item.setId(UUID.randomUUID());
        return item;
    }

    private Batch batch(UUID id, String code, UUID productId, BigDecimal transitQuantity) {
        Product product = new Product();
        product.setId(productId);
        product.setTenantId(tenantId);
        product.setName("Product");
        product.setSku("SKU");
        Batch batch = Batch.builder()
                .product(product)
                .warehouse(sourceWarehouse)
                .batchCode(code)
                .quantity(BigDecimal.ZERO)
                .transitQuantity(transitQuantity)
                .costPrice(100L)
                .sellingPrice(200L)
                .build();
        batch.setId(id);
        batch.setTenantId(tenantId);
        savedBatches.put(id, batch);
        return batch;
    }

    private Warehouse warehouse(UUID id, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setTenantId(tenantId);
        warehouse.setName(name);
        warehouse.setCode(name.toUpperCase());
        warehouse.setCity("Recife");
        warehouse.setState("PE");
        warehouse.setIsActive(true);
        return warehouse;
    }
}
