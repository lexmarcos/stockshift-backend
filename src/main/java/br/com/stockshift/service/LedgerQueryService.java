package br.com.stockshift.service;

import br.com.stockshift.model.entity.InventoryLedger;
import br.com.stockshift.repository.InventoryLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerQueryService {

    private final InventoryLedgerRepository ledgerRepository;

    public List<InventoryLedger> findByTransferId(UUID transferId) {
        return ledgerRepository.findByReferenceTypeAndReferenceId("TRANSFER", transferId);
    }

    public List<InventoryLedger> findByBatchId(UUID batchId) {
        return ledgerRepository.findByBatchIdOrderByCreatedAtDesc(batchId);
    }

    public List<InventoryLedger> findByWarehouseId(UUID warehouseId) {
        return ledgerRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId);
    }

    public List<InventoryLedger> findByProductId(UUID productId) {
        return ledgerRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }
}
