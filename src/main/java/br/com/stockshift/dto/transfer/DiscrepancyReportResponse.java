package br.com.stockshift.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyReportResponse {

    private UUID transferId;
    private String transferCode;
    private String sourceWarehouseName;
    private String destinationWarehouseName;
    private Instant completedAt;
    private List<CompleteValidationResponse.DiscrepancyItem> discrepancies;
    private BigDecimal totalShortage;
    private BigDecimal totalOverage;
}
