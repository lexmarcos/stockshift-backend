package br.com.stockshift.service;

import br.com.stockshift.dto.ReconciliationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AlertService {

    public void sendCriticalAlert(String subject, List<ReconciliationResult> discrepancies) {
        log.error("CRITICAL ALERT: {} - {} discrepancies found", subject, discrepancies.size());
        for (ReconciliationResult discrepancy : discrepancies) {
            log.error("  Batch {}: materialized={}, calculated={}, diff={}",
                discrepancy.batchCode(),
                discrepancy.materializedQuantity(),
                discrepancy.calculatedQuantity(),
                discrepancy.difference()
            );
        }
        // TODO: Integrate with actual alerting system (email, Slack, PagerDuty, etc.)
    }
}
