package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardKpisResponse {
    private KpiPeriodData currentMonth;
    private KpiPeriodData previousMonth;
    private KpiVariations variations;
}
