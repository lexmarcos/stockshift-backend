package br.com.stockshift.service.transfer;

import br.com.stockshift.model.entity.NewTransferDiscrepancy;
import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.entity.TransferItem;
import br.com.stockshift.model.enums.DiscrepancyStatus;
import br.com.stockshift.model.enums.DiscrepancyType;
import br.com.stockshift.repository.NewTransferDiscrepancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscrepancyService {

    private final NewTransferDiscrepancyRepository discrepancyRepository;

    public record ValidationResult(
        boolean hasDiscrepancy,
        DiscrepancyType discrepancyType,
        BigDecimal difference
    ) {}

    public ValidationResult evaluateItem(TransferItem item) {
        BigDecimal expected = item.getExpectedQuantity();
        BigDecimal received = item.getReceivedQuantity() != null
            ? item.getReceivedQuantity()
            : BigDecimal.ZERO;

        int comparison = received.compareTo(expected);

        if (comparison == 0) {
            return new ValidationResult(false, null, BigDecimal.ZERO);
        } else if (comparison < 0) {
            BigDecimal difference = expected.subtract(received);
            return new ValidationResult(true, DiscrepancyType.SHORTAGE, difference);
        } else {
            BigDecimal difference = received.subtract(expected);
            return new ValidationResult(true, DiscrepancyType.EXCESS, difference);
        }
    }

    public NewTransferDiscrepancy createDiscrepancy(
        Transfer transfer,
        TransferItem item,
        DiscrepancyType type
    ) {
        BigDecimal expected = item.getExpectedQuantity();
        BigDecimal received = item.getReceivedQuantity() != null
            ? item.getReceivedQuantity()
            : BigDecimal.ZERO;
        BigDecimal difference = type == DiscrepancyType.SHORTAGE
            ? expected.subtract(received)
            : received.subtract(expected);

        NewTransferDiscrepancy discrepancy = new NewTransferDiscrepancy();
        discrepancy.setTenantId(transfer.getTenantId());
        discrepancy.setTransfer(transfer);
        discrepancy.setTransferItem(item);
        discrepancy.setDiscrepancyType(type);
        discrepancy.setExpectedQuantity(expected);
        discrepancy.setReceivedQuantity(received);
        discrepancy.setDifference(difference);
        discrepancy.setStatus(DiscrepancyStatus.PENDING_RESOLUTION);

        log.info("Creating {} discrepancy for transfer {} item {}, difference: {}",
            type, transfer.getId(), item.getId(), difference);

        return discrepancyRepository.save(discrepancy);
    }

    public List<NewTransferDiscrepancy> findByTransferId(UUID transferId) {
        return discrepancyRepository.findByTransferId(transferId);
    }

    public List<NewTransferDiscrepancy> findPendingByTenantId(UUID tenantId) {
        return discrepancyRepository.findPendingByTenantId(tenantId);
    }
}
