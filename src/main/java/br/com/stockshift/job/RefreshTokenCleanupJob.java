package br.com.stockshift.job;

import br.com.stockshift.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupJob {

    private final RefreshTokenService refreshTokenService;

    // Daily at 03:00. Refresh tokens expire after 7 days but are otherwise only
    // pruned on rotation/logout, so abandoned sessions would leak their last row
    // indefinitely without this sweep.
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredRefreshTokens() {
        int purged = refreshTokenService.purgeExpiredTokens();
        if (purged > 0) {
            log.info("Purged {} expired refresh tokens", purged);
        }
    }
}
