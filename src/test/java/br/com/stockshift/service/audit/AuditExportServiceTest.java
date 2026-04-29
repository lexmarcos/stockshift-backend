package br.com.stockshift.service.audit;

import br.com.stockshift.model.entity.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditExportServiceTest {

    private final AuditExportService service = new AuditExportService(new ObjectMapper().findAndRegisterModules());

    @Test
    void csvShouldEscapeCommasQuotesAndLineBreaks() {
        AuditEvent event = auditEvent();
        event.setActorEmail("user, \"quoted\"\nline@test.com");

        String csv = new String(service.toCsv(List.of(event)), StandardCharsets.UTF_8);

        assertThat(csv).contains("\"user, \"\"quoted\"\"\nline@test.com\"");
        assertThat(csv).contains("PRODUCT:").contains("PRODUCT_CREATED");
    }

    @Test
    void xlsxShouldContainHeadersAndRows() throws Exception {
        byte[] xlsx = service.toXlsx(List.of(auditEvent()));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("data");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(15).getStringCellValue())
                    .isEqualTo("campos_alterados");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(6).getStringCellValue())
                    .isEqualTo("PRODUCT_CREATED");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(16).getStringCellValue())
                    .contains("source");
        }
    }

    private AuditEvent auditEvent() {
        UUID resourceId = UUID.randomUUID();
        return AuditEvent.builder()
                .tenantId(UUID.randomUUID())
                .occurredAt(LocalDateTime.of(2026, 4, 29, 10, 0))
                .actorUserId(UUID.randomUUID())
                .actorEmail("user@test.com")
                .warehouseId(UUID.randomUUID())
                .operation(AuditService.OPERATION_TECHNICAL)
                .action("PRODUCT_CREATED")
                .outcome(AuditService.OUTCOME_SUCCESS)
                .resourceType("PRODUCT")
                .resourceId(resourceId.toString())
                .requestId("req-1")
                .ipAddress("127.0.0.1")
                .httpMethod("POST")
                .httpPath("/api/products")
                .httpStatus(201)
                .changedFields(List.of("name"))
                .metadata(Map.of("source", "test"))
                .build();
    }
}
