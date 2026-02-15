package br.com.stockshift.service.transfer;

import br.com.stockshift.dto.transfer.TransferResponse;
import br.com.stockshift.mapper.TransferMapper;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.InventoryLedger;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

    @InjectMocks
    private TransferService transferService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(batchRepository.findByIdForUpdate(sourceBatchId)).thenReturn(Optional.of(batch));
        when(batchRepository.save(any(Batch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(transfer)).thenReturn(transfer);
        when(transferMapper.toResponse(eq(transfer), eq("Recife"), eq("Natal")))
                .thenReturn(TransferResponse.builder().id(transferId).status(TransferStatus.IN_TRANSIT).build());

        transferService.execute(transferId);

        ArgumentCaptor<InventoryLedger> ledgerCaptor = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        InventoryLedger savedLedger = ledgerCaptor.getValue();

        assertThat(savedLedger.getTenantId()).isEqualTo(tenantId);
        assertThat(savedLedger.getCreatedBy()).isEqualTo(userId);
    }
}
