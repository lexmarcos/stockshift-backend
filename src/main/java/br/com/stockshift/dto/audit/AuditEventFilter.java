package br.com.stockshift.dto.audit;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AuditEventFilter(
        UUID tenantId,
        UUID actorUserId,
        String resourceType,
        String resourceId,
        String operation,
        String action,
        String outcome,
        LocalDateTime dateFrom,
        LocalDateTime dateTo) {
}
