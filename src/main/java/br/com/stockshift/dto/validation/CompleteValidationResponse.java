package br.com.stockshift.dto.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteValidationResponse {
    private UUID validationId;
    private String status;
    private LocalDateTime completedAt;
    private ValidationSummary summary;
    private List<DiscrepancyResponse> discrepancies;
    private String reportUrl;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationSummary {
        private int totalExpected;
        private int totalReceived;
        private int totalMissing;
    }
}
