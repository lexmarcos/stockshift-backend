package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteValidationResponse {

    private UUID transferId;
    private TransferStatus status;
    private ValidationSummary summary;
    private List<DiscrepancyItem> discrepancies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationSummary {
        private int totalItemTypes;
        private int itemsOk;
        private int itemsWithDiscrepancy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscrepancyItem {
        private String productName;
        private String productBarcode;
        private java.math.BigDecimal quantitySent;
        private java.math.BigDecimal quantityReceived;
        private java.math.BigDecimal difference;
        private DiscrepancyType type;
    }

    public enum DiscrepancyType {
        SHORTAGE,
        OVERAGE
    }
}
