package br.com.stockshift.service;

import br.com.stockshift.model.entity.InventoryLedger;
import br.com.stockshift.model.enums.LedgerEntryType;
import br.com.stockshift.repository.InventoryLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerQueryServiceTest {

    @Mock
    private InventoryLedgerRepository ledgerRepository;

    private LedgerQueryService ledgerQueryService;

    @BeforeEach
    void setUp() {
        ledgerQueryService = new LedgerQueryService(ledgerRepository);
    }

    @Test
    void shouldFindLedgerEntriesByTransferId() {
        UUID transferId = UUID.randomUUID();

        InventoryLedger entry1 = new InventoryLedger();
        entry1.setId(UUID.randomUUID());
        entry1.setEntryType(LedgerEntryType.TRANSFER_OUT);
        entry1.setQuantity(new BigDecimal("50"));

        InventoryLedger entry2 = new InventoryLedger();
        entry2.setId(UUID.randomUUID());
        entry2.setEntryType(LedgerEntryType.TRANSFER_IN_TRANSIT);
        entry2.setQuantity(new BigDecimal("50"));

        when(ledgerRepository.findByReferenceTypeAndReferenceId("TRANSFER", transferId))
            .thenReturn(List.of(entry1, entry2));

        List<InventoryLedger> result = ledgerQueryService.findByTransferId(transferId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEntryType()).isEqualTo(LedgerEntryType.TRANSFER_OUT);
    }

    @Test
    void shouldFindLedgerEntriesByBatchId() {
        UUID batchId = UUID.randomUUID();

        when(ledgerRepository.findByBatchIdOrderByCreatedAtDesc(batchId))
            .thenReturn(List.of());

        List<InventoryLedger> result = ledgerQueryService.findByBatchId(batchId);

        assertThat(result).isNotNull();
    }
}
