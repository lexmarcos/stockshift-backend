package br.com.stockshift.service.audit;

import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record AuditEventCreateRequest(
        UUID tenantId,
        UUID actorUserId,
        String actorEmail,
        UUID warehouseId,
        String operation,
        String action,
        String outcome,
        String resourceType,
        String resourceId,
        String reason,
        Integer httpStatus,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        List<String> changedFields,
        Map<String, Object> metadata) {
}
