package br.com.stockshift.dto.report;

import br.com.stockshift.model.enums.StockMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentMovementAlert {
    private StockMovementType movementType;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal value;
    private LocalDate date;
}
