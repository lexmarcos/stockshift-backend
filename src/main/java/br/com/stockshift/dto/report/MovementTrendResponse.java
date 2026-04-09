package br.com.stockshift.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementTrendResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<DailyMovement> days;
    private MovementTotals totals;
}
