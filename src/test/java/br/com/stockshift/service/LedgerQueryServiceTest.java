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
    void shouldFindLedgerEntriesByBatchId() {
        UUID batchId = UUID.randomUUID();

        when(ledgerRepository.findByBatchIdOrderByCreatedAtDesc(batchId))
            .thenReturn(List.of());

        List<InventoryLedger> result = ledgerQueryService.findByBatchId(batchId);

        assertThat(result).isNotNull();
    }
}
