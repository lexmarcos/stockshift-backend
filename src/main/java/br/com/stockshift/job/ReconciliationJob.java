package br.com.stockshift.job;

import br.com.stockshift.dto.ReconciliationResult;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.service.AlertService;
import br.com.stockshift.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJob {

    private final TenantRepository tenantRepository;
    private final ReconciliationService reconciliationService;
    private final AlertService alertService;

    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void runDailyReconciliation() {
        log.info("Starting daily batch quantity reconciliation");

        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            try {
                List<ReconciliationResult> discrepancies =
                    reconciliationService.reconcileTenant(tenant.getId());

                if (!discrepancies.isEmpty()) {
                    log.warn("Found {} discrepancies for tenant {}",
                        discrepancies.size(), tenant.getBusinessName());
                    alertService.sendCriticalAlert(
                        "Batch quantity mismatch detected",
                        discrepancies
                    );
                }
            } catch (Exception e) {
                log.error("Error reconciling tenant {}: {}", tenant.getId(), e.getMessage(), e);
            }
        }

        log.info("Daily batch quantity reconciliation completed");
    }
}
