package br.com.stockshift.security.audit;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuditContext {
    private UUID tenantId;
    private UUID actorUserId;
    private String actorEmail;
    private UUID warehouseId;
    private String requestId;
    private String httpMethod;
    private String httpPath;
    private Integer httpStatus;
    private String ipAddress;
    private String userAgent;
}
