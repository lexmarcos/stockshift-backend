package br.com.stockshift.repository;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.NewTransferDiscrepancy;
import br.com.stockshift.model.enums.DiscrepancyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NewTransferDiscrepancyRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private NewTransferDiscrepancyRepository repository;

    @Test
    void shouldFindByTransferId() {
        UUID transferId = UUID.randomUUID();
        List<NewTransferDiscrepancy> result = repository.findByTransferId(transferId);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldFindByTransferIdAndStatus() {
        UUID transferId = UUID.randomUUID();
        List<NewTransferDiscrepancy> result = repository.findByTransferIdAndStatus(
            transferId, DiscrepancyStatus.PENDING_RESOLUTION
        );
        assertThat(result).isNotNull();
    }

    @Test
    void shouldFindPendingByTenantId() {
        UUID tenantId = UUID.randomUUID();
        List<NewTransferDiscrepancy> result = repository.findPendingByTenantId(tenantId);
        assertThat(result).isNotNull();
    }
}
