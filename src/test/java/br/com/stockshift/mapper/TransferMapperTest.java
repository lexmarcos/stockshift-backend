package br.com.stockshift.mapper;

import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferItem;
import br.com.stockshift.model.entity.TransferValidationLog;
import br.com.stockshift.model.enums.TransferStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransferMapperTest {

    private final TransferMapper mapper = new TransferMapper();

    @Test
    void toResponseShouldMapTransferLifecycleFieldsAndItems() {
        Transfer transfer = Transfer.builder()
                .code("TRF-2026-0001")
                .sourceWarehouseId(UUID.randomUUID())
                .destinationWarehouseId(UUID.randomUUID())
                .status(TransferStatus.COMPLETED)
                .notes("notes")
                .createdByUserId(UUID.randomUUID())
                .executedByUserId(UUID.randomUUID())
                .executedAt(Instant.now())
                .validatedByUserId(UUID.randomUUID())
                .validatedAt(Instant.now())
                .cancelledByUserId(UUID.randomUUID())
                .cancelledAt(Instant.now())
                .cancellationReason("reason")
                .build();
        transfer.setId(UUID.randomUUID());
        transfer.setCreatedAt(LocalDateTime.now());
        transfer.setUpdatedAt(LocalDateTime.now());
        transfer.addItem(item());

        var response = mapper.toResponse(transfer, "Source", "Destination");

        assertThat(response.getId()).isEqualTo(transfer.getId());
        assertThat(response.getSourceWarehouseName()).isEqualTo("Source");
        assertThat(response.getDestinationWarehouseName()).isEqualTo("Destination");
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
        assertThat(response.getItems()).hasSize(1);
    }

    @Test
    void shouldMapItemsAndValidationLogs() {
        TransferValidationLog log = TransferValidationLog.builder()
                .transferId(UUID.randomUUID())
                .transferItemId(UUID.randomUUID())
                .barcode("ABC")
                .validatedByUserId(UUID.randomUUID())
                .validatedAt(Instant.now())
                .valid(true)
                .build();
        log.setId(UUID.randomUUID());

        assertThat(mapper.toItemResponseList(List.of(item(), item()))).hasSize(2);
        assertThat(mapper.toValidationLogResponse(log).getBarcode()).isEqualTo("ABC");
        assertThat(mapper.toValidationLogResponseList(List.of(log))).hasSize(1);
    }

    private TransferItem item() {
        TransferItem item = TransferItem.builder()
                .sourceBatchId(UUID.randomUUID())
                .batchCode("B1")
                .productId(UUID.randomUUID())
                .productBarcode("ABC")
                .productName("Product")
                .productSku("SKU")
                .quantitySent(new BigDecimal("2"))
                .quantityReceived(BigDecimal.ONE)
                .destinationBatchId(UUID.randomUUID())
                .build();
        item.setId(UUID.randomUUID());
        return item;
    }
}
