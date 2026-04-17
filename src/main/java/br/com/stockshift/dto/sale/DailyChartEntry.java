package br.com.stockshift.dto.sale;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyChartEntry {
    private String date;
    private long count;
    private long revenue;
}
