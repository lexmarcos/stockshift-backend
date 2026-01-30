package br.com.stockshift.model.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DiscrepancyEnumsTest {

    @Test
    void discrepancyTypeShouldHaveShortageAndExcess() {
        assertThat(DiscrepancyType.values()).containsExactlyInAnyOrder(
            DiscrepancyType.SHORTAGE,
            DiscrepancyType.EXCESS
        );
    }

    @Test
    void discrepancyStatusShouldHaveAllStatuses() {
        assertThat(DiscrepancyStatus.values()).containsExactlyInAnyOrder(
            DiscrepancyStatus.PENDING_RESOLUTION,
            DiscrepancyStatus.RESOLVED,
            DiscrepancyStatus.WRITTEN_OFF
        );
    }

    @Test
    void discrepancyResolutionShouldHaveAllResolutions() {
        assertThat(DiscrepancyResolution.values()).containsExactlyInAnyOrder(
            DiscrepancyResolution.WRITE_OFF,
            DiscrepancyResolution.FOUND,
            DiscrepancyResolution.RETURN_TRANSIT,
            DiscrepancyResolution.ACCEPTED
        );
    }
}
