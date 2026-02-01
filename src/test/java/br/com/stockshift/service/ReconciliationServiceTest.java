package br.com.stockshift.service;

import br.com.stockshift.dto.ReconciliationResult;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.InventoryLedger;
import br.com.stockshift.model.enums.LedgerEntryType;
import br.com.stockshift.repository.BatchRepository;
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
class ReconciliationServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private InventoryLedgerRepository ledgerRepository;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(batchRepository, ledgerRepository);
    }

    @Test
    void shouldDetectNoDiscrepancyWhenBalanced() {
        UUID tenantId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        Batch batch = new Batch();
        batch.setId(batchId);
        batch.setQuantity(new BigDecimal("50"));

        InventoryLedger entry1 = new InventoryLedger();
        entry1.setEntryType(LedgerEntryType.PURCHASE_IN); // Credit
        entry1.setQuantity(new BigDecimal("100"));

        InventoryLedger entry2 = new InventoryLedger();
        entry2.setEntryType(LedgerEntryType.ADJUSTMENT_OUT); // Debit
        entry2.setQuantity(new BigDecimal("50"));

        when(batchRepository.findAllByTenantId(tenantId)).thenReturn(List.of(batch));
        when(ledgerRepository.findByBatchId(batchId)).thenReturn(List.of(entry1, entry2));

        List<ReconciliationResult> results = reconciliationService.reconcileTenant(tenantId);

        assertThat(results).isEmpty();
    }

    @Test
    void shouldDetectDiscrepancyWhenUnbalanced() {
        UUID tenantId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        Batch batch = new Batch();
        batch.setId(batchId);
        batch.setBatchCode("BATCH-001");
        batch.setQuantity(new BigDecimal("60")); // Wrong! Should be 50

        InventoryLedger entry1 = new InventoryLedger();
        entry1.setEntryType(LedgerEntryType.PURCHASE_IN);
        entry1.setQuantity(new BigDecimal("100"));

        InventoryLedger entry2 = new InventoryLedger();
        entry2.setEntryType(LedgerEntryType.ADJUSTMENT_OUT);
        entry2.setQuantity(new BigDecimal("50"));

        when(batchRepository.findAllByTenantId(tenantId)).thenReturn(List.of(batch));
        when(ledgerRepository.findByBatchId(batchId)).thenReturn(List.of(entry1, entry2));

        List<ReconciliationResult> results = reconciliationService.reconcileTenant(tenantId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).batchId()).isEqualTo(batchId);
        assertThat(results.get(0).materializedQuantity()).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(results.get(0).calculatedQuantity()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(results.get(0).difference()).isEqualByComparingTo(new BigDecimal("10"));
    }
}
