package br.com.stockshift.controller;

import br.com.stockshift.dto.audit.AuditEventResponse;
import br.com.stockshift.model.entity.AuditEvent;
import br.com.stockshift.service.audit.AuditExportService;
import br.com.stockshift.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock
    private AuditService auditService;
    @Mock
    private AuditExportService auditExportService;

    @Test
    void shouldWrapEventQueriesAndExports() {
        AuditController controller = new AuditController(auditService, auditExportService);
        UUID actorId = UUID.randomUUID();
        LocalDateTime from = LocalDateTime.of(2026, 4, 1, 10, 0);
        LocalDateTime to = LocalDateTime.of(2026, 4, 2, 10, 0);
        AuditController.AuditQueryParams params = new AuditController.AuditQueryParams(
                actorId, "SALE", "sale-1", "BUSINESS", "SALE_CREATED", "SUCCESS", from, to);
        AuditEventResponse response = AuditEventResponse.builder().resourceType("SALE").build();
        AuditEvent event = AuditEvent.builder().resourceType("SALE").build();
        when(auditService.findEvents(any(), any())).thenReturn(new PageImpl<>(List.of(response)));
        when(auditService.findResourceEvents(eq("SALE"), eq("sale-1"), any()))
                .thenReturn(new PageImpl<>(List.of(response)));
        when(auditService.findEventsForExport(any(), eq(100))).thenReturn(List.of(event));
        when(auditExportService.toCsv(List.of(event))).thenReturn("csv".getBytes());
        when(auditExportService.toXlsx(List.of(event))).thenReturn(new byte[] { 1, 2, 3 });

        assertThat(controller.events(params, PageRequest.of(0, 10)).getBody().getData().getContent()).hasSize(1);
        assertThat(controller.resourceEvents("SALE", "sale-1", PageRequest.of(0, 10))
                .getBody().getData().getContent()).hasSize(1);
        assertThat(controller.exportCsv(params, 100).getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("audit-events-20260401100000-20260402100000.csv");
        assertThat(controller.exportXlsx(params, 100).getBody()).containsExactly(1, 2, 3);

        AuditController.AuditQueryParams allDates = new AuditController.AuditQueryParams(
                null, null, null, null, null, null, null, null);
        when(auditService.findEventsForExport(any(), eq(10))).thenReturn(List.of());
        when(auditExportService.toCsv(List.of())).thenReturn(new byte[0]);
        assertThat(controller.exportCsv(allDates, 10).getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("audit-events-all-all.csv");
    }
}
