package br.com.stockshift.job;

import br.com.stockshift.repository.ScanLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScanLogCleanupJob {

    private final ScanLogRepository scanLogRepository;

    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredScanLogs() {
        log.info("Starting expired scan logs cleanup job");
        try {
            scanLogRepository.deleteExpiredBefore(LocalDateTime.now());
            log.info("Finished expired scan logs cleanup job successfully");
        } catch (Exception e) {
            log.error("Error occurred during expired scan logs cleanup job", e);
        }
    }
}
