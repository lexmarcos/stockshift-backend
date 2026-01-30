package br.com.stockshift.service.transfer;

import br.com.stockshift.model.entity.*;
import br.com.stockshift.model.enums.*;
import br.com.stockshift.repository.NewTransferDiscrepancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscrepancyServiceTest {

    @Mock
    private NewTransferDiscrepancyRepository discrepancyRepository;

    @Captor
    private ArgumentCaptor<NewTransferDiscrepancy> discrepancyCaptor;

    private DiscrepancyService discrepancyService;

    private Transfer transfer;
    private TransferItem item;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        discrepancyService = new DiscrepancyService(discrepancyRepository);

        tenantId = UUID.randomUUID();

        transfer = new Transfer();
        transfer.setId(UUID.randomUUID());
        transfer.setTenantId(tenantId);

        item = new TransferItem();
        item.setId(UUID.randomUUID());
        item.setTenantId(tenantId);
        item.setExpectedQuantity(new BigDecimal("50"));
    }

    @Nested
    class CreateDiscrepancy {

        @Test
        void shouldCreateShortageDiscrepancy() {
            item.setReceivedQuantity(new BigDecimal("40"));

            when(discrepancyRepository.save(any())).thenAnswer(inv -> {
                NewTransferDiscrepancy d = inv.getArgument(0);
                d.setId(UUID.randomUUID());
                return d;
            });

            NewTransferDiscrepancy result = discrepancyService.createDiscrepancy(
                transfer, item, DiscrepancyType.SHORTAGE
            );

            assertThat(result.getDiscrepancyType()).isEqualTo(DiscrepancyType.SHORTAGE);
            assertThat(result.getExpectedQuantity()).isEqualByComparingTo(new BigDecimal("50"));
            assertThat(result.getReceivedQuantity()).isEqualByComparingTo(new BigDecimal("40"));
            assertThat(result.getDifference()).isEqualByComparingTo(new BigDecimal("10"));
            assertThat(result.getStatus()).isEqualTo(DiscrepancyStatus.PENDING_RESOLUTION);
        }

        @Test
        void shouldCreateExcessDiscrepancy() {
            item.setReceivedQuantity(new BigDecimal("60"));

            when(discrepancyRepository.save(any())).thenAnswer(inv -> {
                NewTransferDiscrepancy d = inv.getArgument(0);
                d.setId(UUID.randomUUID());
                return d;
            });

            NewTransferDiscrepancy result = discrepancyService.createDiscrepancy(
                transfer, item, DiscrepancyType.EXCESS
            );

            assertThat(result.getDiscrepancyType()).isEqualTo(DiscrepancyType.EXCESS);
            assertThat(result.getDifference()).isEqualByComparingTo(new BigDecimal("10"));
        }
    }

    @Nested
    class EvaluateValidation {

        @Test
        void shouldDetectNoDiscrepancy() {
            item.setReceivedQuantity(new BigDecimal("50"));

            DiscrepancyService.ValidationResult result = discrepancyService.evaluateItem(item);

            assertThat(result.hasDiscrepancy()).isFalse();
            assertThat(result.discrepancyType()).isNull();
        }

        @Test
        void shouldDetectShortage() {
            item.setReceivedQuantity(new BigDecimal("40"));

            DiscrepancyService.ValidationResult result = discrepancyService.evaluateItem(item);

            assertThat(result.hasDiscrepancy()).isTrue();
            assertThat(result.discrepancyType()).isEqualTo(DiscrepancyType.SHORTAGE);
            assertThat(result.difference()).isEqualByComparingTo(new BigDecimal("10"));
        }

        @Test
        void shouldDetectExcess() {
            item.setReceivedQuantity(new BigDecimal("60"));

            DiscrepancyService.ValidationResult result = discrepancyService.evaluateItem(item);

            assertThat(result.hasDiscrepancy()).isTrue();
            assertThat(result.discrepancyType()).isEqualTo(DiscrepancyType.EXCESS);
            assertThat(result.difference()).isEqualByComparingTo(new BigDecimal("10"));
        }

        @Test
        void shouldTreatNullReceivedAsZero() {
            item.setReceivedQuantity(null);

            DiscrepancyService.ValidationResult result = discrepancyService.evaluateItem(item);

            assertThat(result.hasDiscrepancy()).isTrue();
            assertThat(result.discrepancyType()).isEqualTo(DiscrepancyType.SHORTAGE);
            assertThat(result.difference()).isEqualByComparingTo(new BigDecimal("50"));
        }
    }
}
