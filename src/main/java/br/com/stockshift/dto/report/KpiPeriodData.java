package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiPeriodData {
    private BigDecimal totalStockValue;
    private BigDecimal totalPurchasesValue;
    private BigDecimal totalLossesValue;
    private BigDecimal totalDamageValue;
    private BigDecimal totalGiftValue;
    private BigDecimal totalAdjustmentValue;
    private BigDecimal totalTransitValue;
    private BigDecimal stockTurnoverRate;
}
