package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.audit.AuditEventFilter;
import br.com.stockshift.dto.audit.AuditEventResponse;
import br.com.stockshift.model.entity.AuditEvent;
import br.com.stockshift.service.audit.AuditExportService;
import br.com.stockshift.service.audit.AuditService;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class AuditController {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AuditService auditService;
    private final AuditExportService auditExportService;

    @GetMapping("/events")
    @PreAuthorize("@permissionGuard.has('audit:read')")
    public ResponseEntity<ApiResponse<Page<AuditEventResponse>>> events(
            @ParameterObject @ModelAttribute AuditQueryParams params,
            @ParameterObject @PageableDefault(sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditEventResponse> response = auditService.findEvents(params.toFilter(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Audit events retrieved successfully", response));
    }

    @GetMapping("/resources/{resourceType}/{resourceId}")
    @PreAuthorize("@permissionGuard.has('audit:read')")
    public ResponseEntity<ApiResponse<Page<AuditEventResponse>>> resourceEvents(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @ParameterObject @PageableDefault(sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditEventResponse> response = auditService.findResourceEvents(resourceType, resourceId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Audit resource events retrieved successfully", response));
    }

    @GetMapping("/events/export.csv")
    @PreAuthorize("@permissionGuard.has('audit:read')")
    public ResponseEntity<byte[]> exportCsv(
            @ParameterObject @ModelAttribute AuditQueryParams params,
            @RequestParam(defaultValue = "10000") int limit) {
        List<AuditEvent> events = auditService.findEventsForExport(params.toFilter(), limit);
        byte[] body = auditExportService.toCsv(events);
        return exportResponse(body, "csv", MediaType.parseMediaType("text/csv; charset=UTF-8"), params);
    }

    @GetMapping("/events/export.xlsx")
    @PreAuthorize("@permissionGuard.has('audit:read')")
    public ResponseEntity<byte[]> exportXlsx(
            @ParameterObject @ModelAttribute AuditQueryParams params,
            @RequestParam(defaultValue = "10000") int limit) {
        List<AuditEvent> events = auditService.findEventsForExport(params.toFilter(), limit);
        byte[] body = auditExportService.toXlsx(events);
        return exportResponse(body, "xlsx", XLSX_MEDIA_TYPE, params);
    }

    private ResponseEntity<byte[]> exportResponse(
            byte[] body,
            String extension,
            MediaType mediaType,
            AuditQueryParams params) {
        String filename = "audit-events-" + format(params.dateFrom()) + "-" + format(params.dateTo()) + "." + extension;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }

    private String format(LocalDateTime value) {
        return value != null ? value.format(FILE_DATE_FORMAT) : "all";
    }

    public record AuditQueryParams(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {

        AuditEventFilter toFilter() {
            return AuditEventFilter.builder()
                    .actorUserId(actorUserId)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .operation(operation)
                    .action(action)
                    .outcome(outcome)
                    .dateFrom(dateFrom)
                    .dateTo(dateTo)
                    .build();
        }
    }
}
