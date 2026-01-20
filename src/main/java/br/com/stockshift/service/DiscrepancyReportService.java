package br.com.stockshift.service;

import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.entity.TransferDiscrepancy;
import br.com.stockshift.model.entity.TransferValidation;
import br.com.stockshift.model.enums.ValidationStatus;
import br.com.stockshift.repository.TransferDiscrepancyRepository;
import br.com.stockshift.repository.TransferValidationRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscrepancyReportService {

    private final TransferValidationRepository validationRepository;
    private final TransferDiscrepancyRepository discrepancyRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transactional(readOnly = true)
    public byte[] generatePdfReport(UUID movementId, UUID validationId) {
        TransferValidation validation = getValidation(movementId, validationId);
        List<TransferDiscrepancy> discrepancies = discrepancyRepository.findByTransferValidationId(validationId);

        if (discrepancies.isEmpty()) {
            throw new BusinessException("No discrepancies found for this validation");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Relatório de Discrepâncias", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            StockMovement movement = validation.getStockMovement();
            com.lowagie.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            com.lowagie.text.Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 11);

            document.add(new Paragraph("Transferência: " + movement.getId(), normalFont));
            document.add(new Paragraph("Origem: " + movement.getSourceWarehouse().getName(), normalFont));
            document.add(new Paragraph("Destino: " + movement.getDestinationWarehouse().getName(), normalFont));
            document.add(new Paragraph("Data da Validação: " + validation.getCompletedAt().format(DATE_FORMATTER), normalFont));
            document.add(new Paragraph("Validado por: " + validation.getValidatedBy().getFullName(), normalFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3, 1, 1, 1});

            addTableHeader(table, "Produto", headerFont);
            addTableHeader(table, "Esperado", headerFont);
            addTableHeader(table, "Recebido", headerFont);
            addTableHeader(table, "Faltante", headerFont);

            int totalExpected = 0, totalReceived = 0, totalMissing = 0;
            for (TransferDiscrepancy d : discrepancies) {
                table.addCell(new PdfPCell(new Phrase(d.getStockMovementItem().getProduct().getName(), normalFont)));
                table.addCell(new PdfPCell(new Phrase(String.valueOf(d.getExpectedQuantity()), normalFont)));
                table.addCell(new PdfPCell(new Phrase(String.valueOf(d.getReceivedQuantity()), normalFont)));
                table.addCell(new PdfPCell(new Phrase(String.valueOf(d.getMissingQuantity()), normalFont)));

                totalExpected += d.getExpectedQuantity();
                totalReceived += d.getReceivedQuantity();
                totalMissing += d.getMissingQuantity();
            }

            addTableHeader(table, "TOTAL", headerFont);
            addTableHeader(table, String.valueOf(totalExpected), headerFont);
            addTableHeader(table, String.valueOf(totalReceived), headerFont);
            addTableHeader(table, String.valueOf(totalMissing), headerFont);

            document.add(table);
            document.close();

            log.info("Generated PDF discrepancy report for validation {}", validationId);
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new BusinessException("Error generating PDF report: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateExcelReport(UUID movementId, UUID validationId) {
        TransferValidation validation = getValidation(movementId, validationId);
        List<TransferDiscrepancy> discrepancies = discrepancyRepository.findByTransferValidationId(validationId);

        if (discrepancies.isEmpty()) {
            throw new BusinessException("No discrepancies found for this validation");
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Discrepâncias");

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            StockMovement movement = validation.getStockMovement();
            int rowNum = 0;

            org.apache.poi.ss.usermodel.Row infoRow1 = sheet.createRow(rowNum++);
            infoRow1.createCell(0).setCellValue("Transferência:");
            infoRow1.createCell(1).setCellValue(movement.getId().toString());

            org.apache.poi.ss.usermodel.Row infoRow2 = sheet.createRow(rowNum++);
            infoRow2.createCell(0).setCellValue("Origem:");
            infoRow2.createCell(1).setCellValue(movement.getSourceWarehouse().getName());

            org.apache.poi.ss.usermodel.Row infoRow3 = sheet.createRow(rowNum++);
            infoRow3.createCell(0).setCellValue("Destino:");
            infoRow3.createCell(1).setCellValue(movement.getDestinationWarehouse().getName());

            org.apache.poi.ss.usermodel.Row infoRow4 = sheet.createRow(rowNum++);
            infoRow4.createCell(0).setCellValue("Data da Validação:");
            infoRow4.createCell(1).setCellValue(validation.getCompletedAt().format(DATE_FORMATTER));

            org.apache.poi.ss.usermodel.Row infoRow5 = sheet.createRow(rowNum++);
            infoRow5.createCell(0).setCellValue("Validado por:");
            infoRow5.createCell(1).setCellValue(validation.getValidatedBy().getFullName());

            rowNum++;

            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Produto", "Código de Barras", "Esperado", "Recebido", "Faltante"};
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int totalExpected = 0, totalReceived = 0, totalMissing = 0;
            for (TransferDiscrepancy d : discrepancies) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(d.getStockMovementItem().getProduct().getName());
                row.createCell(1).setCellValue(d.getStockMovementItem().getProduct().getBarcode());
                row.createCell(2).setCellValue(d.getExpectedQuantity());
                row.createCell(3).setCellValue(d.getReceivedQuantity());
                row.createCell(4).setCellValue(d.getMissingQuantity());

                totalExpected += d.getExpectedQuantity();
                totalReceived += d.getReceivedQuantity();
                totalMissing += d.getMissingQuantity();
            }

            org.apache.poi.ss.usermodel.Row totalRow = sheet.createRow(rowNum);
            org.apache.poi.ss.usermodel.Cell totalLabelCell = totalRow.createCell(0);
            totalLabelCell.setCellValue("TOTAL");
            totalLabelCell.setCellStyle(headerStyle);
            totalRow.createCell(2).setCellValue(totalExpected);
            totalRow.createCell(3).setCellValue(totalReceived);
            totalRow.createCell(4).setCellValue(totalMissing);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            log.info("Generated Excel discrepancy report for validation {}", validationId);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Error generating Excel report: " + e.getMessage());
        }
    }

    private TransferValidation getValidation(UUID movementId, UUID validationId) {
        TransferValidation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("TransferValidation", "id", validationId));

        if (!validation.getStockMovement().getId().equals(movementId)) {
            throw new BusinessException("Validation does not belong to this movement");
        }

        if (validation.getStatus() != ValidationStatus.COMPLETED) {
            throw new BusinessException("Report can only be generated for completed validations");
        }

        return validation;
    }

    private void addTableHeader(PdfPTable table, String text, com.lowagie.text.Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
        cell.setPadding(5);
        table.addCell(cell);
    }
}
