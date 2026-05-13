package br.com.stockshift.job;

import br.com.stockshift.service.upload.ProductImageUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductImageUploadCleanupJob {

    private final ProductImageUploadService productImageUploadService;

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredProductImageUploads() {
        int expiredCount = productImageUploadService.cleanupExpiredUploads();
        if (expiredCount > 0) {
            log.info("Expired {} temporary product image uploads", expiredCount);
        }
    }
}
