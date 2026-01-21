package br.com.stockshift.dto.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationSummaryResponse {
    private UUID validationId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String validatedByName;
    private ProgressSummary progress;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgressSummary {
        private int totalExpected;
        private int totalReceived;
    }
}
