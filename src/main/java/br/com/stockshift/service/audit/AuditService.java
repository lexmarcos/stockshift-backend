package br.com.stockshift.service.audit;

import br.com.stockshift.dto.audit.AuditEventFilter;
import br.com.stockshift.dto.audit.AuditEventResponse;
import br.com.stockshift.exception.BadRequestException;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.AuditEvent;
import br.com.stockshift.repository.AuditEventRepository;
import br.com.stockshift.security.TenantContext;
import br.com.stockshift.security.UserPrincipal;
import br.com.stockshift.security.WarehouseContext;
import br.com.stockshift.security.audit.AuditContext;
import br.com.stockshift.security.audit.AuditContextHolder;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String OPERATION_TECHNICAL = "TECHNICAL";
    public static final String OPERATION_BUSINESS = "BUSINESS";
    public static final String OPERATION_SECURITY = "SECURITY";
    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";
    public static final String OUTCOME_DENIED = "DENIED";
    public static final int DEFAULT_EXPORT_LIMIT = 10_000;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "occurredAt");
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "occurredAt",
            "actorUserId",
            "actorEmail",
            "warehouseId",
            "operation",
            "action",
            "outcome",
            "resourceType",
            "resourceId",
            "requestId",
            "httpStatus");

    private final AuditEventRepository auditEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent record(AuditEventCreateRequest request) {
        return auditEventRepository.save(buildEvent(request));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> findEvents(AuditEventFilter filter, Pageable pageable) {
        UUID tenantId = requireTenantId();
        AuditEventFilter tenantFilter = withTenant(filter, tenantId);
        return auditEventRepository.findAll(specification(tenantFilter), withDefaultSort(pageable))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> findResourceEvents(String resourceType, String resourceId, Pageable pageable) {
        AuditEventFilter filter = AuditEventFilter.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .build();
        return findEvents(filter, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> findEventsForExport(AuditEventFilter filter, int limit) {
        validateExportFilter(filter);
        UUID tenantId = requireTenantId();
        PageRequest pageRequest = PageRequest.of(0, normalizeLimit(limit), Sort.by(Sort.Direction.DESC, "occurredAt"));
        return auditEventRepository.findAll(specification(withTenant(filter, tenantId)), pageRequest).getContent();
    }

    private AuditEvent buildEvent(AuditEventCreateRequest request) {
        AuditContext context = AuditContextHolder.get();
        return AuditEvent.builder()
                .tenantId(firstNonNull(request.tenantId(), resolveTenantId(context)))
                .occurredAt(LocalDateTime.now())
                .actorUserId(firstNonNull(request.actorUserId(), resolveActorUserId(context)))
                .actorEmail(firstNonBlank(request.actorEmail(), resolveActorEmail(context)))
                .warehouseId(firstNonNull(request.warehouseId(), resolveWarehouseId(context)))
                .operation(request.operation())
                .action(request.action())
                .outcome(request.outcome())
                .resourceType(request.resourceType())
                .resourceId(request.resourceId())
                .reason(request.reason())
                .requestId(context != null ? context.getRequestId() : null)
                .httpMethod(context != null ? context.getHttpMethod() : null)
                .httpPath(context != null ? context.getHttpPath() : null)
                .httpStatus(firstNonNull(request.httpStatus(), context != null ? context.getHttpStatus() : null))
                .ipAddress(context != null ? context.getIpAddress() : null)
                .userAgent(context != null ? context.getUserAgent() : null)
                .beforeState(request.beforeState())
                .afterState(request.afterState())
                .changedFields(request.changedFields())
                .metadata(request.metadata())
                .build();
    }

    private Specification<AuditEvent> specification(AuditEventFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            addEqual(predicates, criteriaBuilder, root.get("tenantId"), filter.tenantId());
            addEqual(predicates, criteriaBuilder, root.get("actorUserId"), filter.actorUserId());
            addEqual(predicates, criteriaBuilder, root.get("resourceType"), filter.resourceType());
            addEqual(predicates, criteriaBuilder, root.get("resourceId"), filter.resourceId());
            addEqual(predicates, criteriaBuilder, root.get("operation"), filter.operation());
            addEqual(predicates, criteriaBuilder, root.get("action"), filter.action());
            addEqual(predicates, criteriaBuilder, root.get("outcome"), filter.outcome());
            addDateRange(predicates, criteriaBuilder, root.get("occurredAt"), filter.dateFrom(), filter.dateTo());
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private <T> void addEqual(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            jakarta.persistence.criteria.Path<T> path,
            T value) {
        if (value != null && (!(value instanceof String string) || StringUtils.hasText(string))) {
            predicates.add(criteriaBuilder.equal(path, value));
        }
    }

    private void addDateRange(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            jakarta.persistence.criteria.Path<LocalDateTime> path,
            LocalDateTime dateFrom,
            LocalDateTime dateTo) {
        if (dateFrom != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(path, dateFrom));
        }
        if (dateTo != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(path, dateTo));
        }
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return AuditEventResponse.builder()
                .id(event.getId())
                .tenantId(event.getTenantId())
                .occurredAt(event.getOccurredAt())
                .actorUserId(event.getActorUserId())
                .actorEmail(event.getActorEmail())
                .warehouseId(event.getWarehouseId())
                .operation(event.getOperation())
                .action(event.getAction())
                .outcome(event.getOutcome())
                .resourceType(event.getResourceType())
                .resourceId(event.getResourceId())
                .reason(event.getReason())
                .requestId(event.getRequestId())
                .httpMethod(event.getHttpMethod())
                .httpPath(event.getHttpPath())
                .httpStatus(event.getHttpStatus())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .beforeState(event.getBeforeState())
                .afterState(event.getAfterState())
                .changedFields(event.getChangedFields())
                .metadata(event.getMetadata())
                .build();
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new UnauthorizedException("Tenant context is required for audit queries");
        }
        return tenantId;
    }

    private void validateExportFilter(AuditEventFilter filter) {
        if (filter.dateFrom() == null || filter.dateTo() == null) {
            throw new BadRequestException("dateFrom and dateTo are required for audit export");
        }
    }

    private Pageable withDefaultSort(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return PageRequest.of(0, 50, DEFAULT_SORT);
        }
        if (pageable.getSort().isSorted() && hasOnlyAllowedSorts(pageable.getSort())) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
    }

    private boolean hasOnlyAllowedSorts(Sort sort) {
        return sort.stream()
                .map(Sort.Order::getProperty)
                .allMatch(ALLOWED_SORTS::contains);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_EXPORT_LIMIT;
        }
        return Math.min(limit, DEFAULT_EXPORT_LIMIT);
    }

    private AuditEventFilter withTenant(AuditEventFilter filter, UUID tenantId) {
        return AuditEventFilter.builder()
                .tenantId(tenantId)
                .actorUserId(filter.actorUserId())
                .resourceType(filter.resourceType())
                .resourceId(filter.resourceId())
                .operation(filter.operation())
                .action(filter.action())
                .outcome(filter.outcome())
                .dateFrom(filter.dateFrom())
                .dateTo(filter.dateTo())
                .build();
    }

    private UUID resolveTenantId(AuditContext context) {
        return firstNonNull(context != null ? context.getTenantId() : null, TenantContext.getTenantId());
    }

    private UUID resolveActorUserId(AuditContext context) {
        if (context != null && context.getActorUserId() != null) {
            return context.getActorUserId();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                ? principal.getId()
                : null;
    }

    private String resolveActorEmail(AuditContext context) {
        if (context != null && StringUtils.hasText(context.getActorEmail())) {
            return context.getActorEmail();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    private UUID resolveWarehouseId(AuditContext context) {
        return firstNonNull(context != null ? context.getWarehouseId() : null, WarehouseContext.getWarehouseId());
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
