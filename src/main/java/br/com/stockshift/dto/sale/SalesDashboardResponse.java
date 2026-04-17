package br.com.stockshift.dto.sale;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesDashboardResponse {
    private Kpis kpis;
    private List<DailyChartEntry> dailyChart;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Kpis {
        private KpiPeriod today;
        private KpiPeriod week;
        private KpiPeriod month;
    }
}
