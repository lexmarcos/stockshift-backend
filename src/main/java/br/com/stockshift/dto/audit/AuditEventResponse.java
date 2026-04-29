package br.com.stockshift.dto.audit;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record AuditEventResponse(
        UUID id,
        UUID tenantId,
        LocalDateTime occurredAt,
        UUID actorUserId,
        String actorEmail,
        UUID warehouseId,
        String operation,
        String action,
        String outcome,
        String resourceType,
        String resourceId,
        String reason,
        String requestId,
        String httpMethod,
        String httpPath,
        Integer httpStatus,
        String ipAddress,
        String userAgent,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        List<String> changedFields,
        Map<String, Object> metadata) {
}
