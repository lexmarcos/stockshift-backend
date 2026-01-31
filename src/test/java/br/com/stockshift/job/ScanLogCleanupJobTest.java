package br.com.stockshift.job;

import br.com.stockshift.repository.ScanLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScanLogCleanupJobTest {

    @Mock
    private ScanLogRepository scanLogRepository;

    @InjectMocks
    private ScanLogCleanupJob scanLogCleanupJob;

    @Test
    @DisplayName("Should call repository delete method when job runs")
    void shouldCallRepositoryDeleteMethod() {
        scanLogCleanupJob.cleanupExpiredScanLogs();

        verify(scanLogRepository).deleteExpiredBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Should handle repository exceptions gracefully")
    void shouldHandleRepositoryExceptions() {
        doThrow(new RuntimeException("Database error"))
                .when(scanLogRepository).deleteExpiredBefore(any(LocalDateTime.class));

        scanLogCleanupJob.cleanupExpiredScanLogs();

        verify(scanLogRepository).deleteExpiredBefore(any(LocalDateTime.class));
    }
}
