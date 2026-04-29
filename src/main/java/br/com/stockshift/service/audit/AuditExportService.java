package br.com.stockshift.service.audit;

import br.com.stockshift.model.entity.AuditEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditExportService {

    private static final List<String> HEADERS = List.of(
            "data",
            "tenant",
            "usuario",
            "email",
            "warehouse",
            "operacao",
            "acao",
            "resultado",
            "recurso",
            "request_id",
            "ip",
            "metodo",
            "path",
            "status",
            "motivo",
            "campos_alterados",
            "metadata");

    private final ObjectMapper objectMapper;

    public byte[] toCsv(List<AuditEvent> events) {
        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, HEADERS);
        events.stream()
                .map(this::eventColumns)
                .forEach(columns -> appendCsvRow(csv, columns));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] toXlsx(List<AuditEvent> events) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Audit Events");
            writeRow(sheet.createRow(0), HEADERS);
            for (int index = 0; index < events.size(); index++) {
                writeRow(sheet.createRow(index + 1), eventColumns(events.get(index)));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate audit XLSX export", exception);
        }
    }

    private void appendCsvRow(StringBuilder csv, List<String> values) {
        csv.append(values.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse(""));
        csv.append("\n");
    }

    private void writeRow(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            row.createCell(index).setCellValue(values.get(index));
        }
    }

    private List<String> eventColumns(AuditEvent event) {
        return List.of(
                text(event.getOccurredAt()),
                text(event.getTenantId()),
                text(event.getActorUserId()),
                text(event.getActorEmail()),
                text(event.getWarehouseId()),
                text(event.getOperation()),
                text(event.getAction()),
                text(event.getOutcome()),
                resource(event),
                text(event.getRequestId()),
                text(event.getIpAddress()),
                text(event.getHttpMethod()),
                text(event.getHttpPath()),
                text(event.getHttpStatus()),
                text(event.getReason()),
                json(event.getChangedFields()),
                json(event.getMetadata()));
    }

    private String resource(AuditEvent event) {
        if (event.getResourceType() == null) {
            return text(event.getResourceId());
        }
        if (event.getResourceId() == null) {
            return event.getResourceType();
        }
        return event.getResourceType() + ":" + event.getResourceId();
    }

    private String escapeCsv(String value) {
        String safe = value != null ? value : "";
        if (!safe.contains(",") && !safe.contains("\"") && !safe.contains("\n") && !safe.contains("\r")) {
            return safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String json(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return text(value);
        }
    }

    private String text(Object value) {
        return value != null ? String.valueOf(value) : "";
    }
}
