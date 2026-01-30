package br.com.stockshift.model.entity;

import br.com.stockshift.model.enums.DiscrepancyResolution;
import br.com.stockshift.model.enums.DiscrepancyStatus;
import br.com.stockshift.model.enums.DiscrepancyType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NewTransferDiscrepancyTest {

    @Test
    void shouldCreateDiscrepancyWithAllFields() {
        NewTransferDiscrepancy discrepancy = new NewTransferDiscrepancy();
        discrepancy.setTenantId(UUID.randomUUID());
        discrepancy.setDiscrepancyType(DiscrepancyType.SHORTAGE);
        discrepancy.setExpectedQuantity(new BigDecimal("50"));
        discrepancy.setReceivedQuantity(new BigDecimal("40"));
        discrepancy.setDifference(new BigDecimal("10"));
        discrepancy.setStatus(DiscrepancyStatus.PENDING_RESOLUTION);

        assertThat(discrepancy.getDiscrepancyType()).isEqualTo(DiscrepancyType.SHORTAGE);
        assertThat(discrepancy.getStatus()).isEqualTo(DiscrepancyStatus.PENDING_RESOLUTION);
        assertThat(discrepancy.getDifference()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void shouldAllowResolution() {
        NewTransferDiscrepancy discrepancy = new NewTransferDiscrepancy();
        discrepancy.setStatus(DiscrepancyStatus.RESOLVED);
        discrepancy.setResolution(DiscrepancyResolution.WRITE_OFF);
        discrepancy.setResolutionNotes("Damage during transport");

        assertThat(discrepancy.getStatus()).isEqualTo(DiscrepancyStatus.RESOLVED);
        assertThat(discrepancy.getResolution()).isEqualTo(DiscrepancyResolution.WRITE_OFF);
    }
}
