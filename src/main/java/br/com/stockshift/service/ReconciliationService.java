package br.com.stockshift.service;

import br.com.stockshift.dto.ReconciliationResult;
import br.com.stockshift.model.entity.Batch;
import br.com.stockshift.model.entity.InventoryLedger;
import br.com.stockshift.repository.BatchRepository;
import br.com.stockshift.repository.InventoryLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final BatchRepository batchRepository;
    private final InventoryLedgerRepository ledgerRepository;

    @Transactional(readOnly = true)
    public List<ReconciliationResult> reconcileTenant(UUID tenantId) {
        log.info("Starting reconciliation for tenant {}", tenantId);

        List<Batch> batches = batchRepository.findAllByTenantId(tenantId);
        List<ReconciliationResult> discrepancies = new ArrayList<>();

        for (Batch batch : batches) {
            BigDecimal calculatedQuantity = calculateQuantityFromLedger(batch.getId());
            BigDecimal materializedQuantity = batch.getQuantity();

            if (calculatedQuantity.compareTo(materializedQuantity) != 0) {
                BigDecimal difference = materializedQuantity.subtract(calculatedQuantity);
                discrepancies.add(new ReconciliationResult(
                    batch.getId(),
                    batch.getBatchCode(),
                    materializedQuantity,
                    calculatedQuantity,
                    difference
                ));
                log.warn("Discrepancy found in batch {}: materialized={}, calculated={}, diff={}",
                    batch.getBatchCode(), materializedQuantity, calculatedQuantity, difference);
            }
        }

        log.info("Reconciliation complete for tenant {}. Found {} discrepancies",
            tenantId, discrepancies.size());
        return discrepancies;
    }

    public BigDecimal calculateQuantityFromLedger(UUID batchId) {
        List<InventoryLedger> entries = ledgerRepository.findByBatchId(batchId);

        BigDecimal total = BigDecimal.ZERO;
        for (InventoryLedger entry : entries) {
            if (entry.getEntryType().isDebit()) {
                total = total.subtract(entry.getQuantity());
            } else {
                total = total.add(entry.getQuantity());
            }
        }

        return total;
    }
}
