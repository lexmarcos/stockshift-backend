package br.com.stockshift.job;

import br.com.stockshift.dto.ReconciliationResult;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.service.AlertService;
import br.com.stockshift.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationJobTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ReconciliationService reconciliationService;

    @Mock
    private AlertService alertService;

    private ReconciliationJob reconciliationJob;

    @BeforeEach
    void setUp() {
        reconciliationJob = new ReconciliationJob(tenantRepository, reconciliationService, alertService);
    }

    @Test
    void shouldRunReconciliationForAllTenants() {
        Tenant tenant1 = new Tenant();
        tenant1.setId(UUID.randomUUID());
        tenant1.setBusinessName("Tenant 1");

        Tenant tenant2 = new Tenant();
        tenant2.setId(UUID.randomUUID());
        tenant2.setBusinessName("Tenant 2");

        when(tenantRepository.findAll()).thenReturn(List.of(tenant1, tenant2));
        when(reconciliationService.reconcileTenant(any())).thenReturn(List.of());

        reconciliationJob.runDailyReconciliation();

        verify(reconciliationService).reconcileTenant(tenant1.getId());
        verify(reconciliationService).reconcileTenant(tenant2.getId());
        verify(alertService, never()).sendCriticalAlert(any(), any());
    }

    @Test
    void shouldSendAlertWhenDiscrepanciesFound() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setBusinessName("Problem Tenant");

        ReconciliationResult discrepancy = new ReconciliationResult(
            UUID.randomUUID(),
            "BATCH-001",
            new BigDecimal("60"),
            new BigDecimal("50"),
            new BigDecimal("10")
        );

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(reconciliationService.reconcileTenant(tenant.getId())).thenReturn(List.of(discrepancy));

        reconciliationJob.runDailyReconciliation();

        verify(alertService).sendCriticalAlert(
            eq("Batch quantity mismatch detected"),
            eq(List.of(discrepancy))
        );
    }
}
