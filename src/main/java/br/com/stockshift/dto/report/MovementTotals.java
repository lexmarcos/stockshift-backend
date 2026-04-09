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
public class MovementTotals {
    private BigDecimal totalInQuantity;
    private BigDecimal totalInValue;
    private BigDecimal totalOutQuantity;
    private BigDecimal totalOutValue;
    private Long movementCount;
}
