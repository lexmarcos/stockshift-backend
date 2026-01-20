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
public class ValidationProgressResponse {
    private UUID validationId;
    private String status;
    private LocalDateTime startedAt;
    private List<ValidationItemResponse> items;
    private ProgressSummary progress;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgressSummary {
        private int totalItems;
        private int completeItems;
        private int partialItems;
        private int pendingItems;
    }
}
