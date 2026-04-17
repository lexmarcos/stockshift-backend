package br.com.stockshift.dto.sale;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiPeriod {
    private long count;
    private long revenue;
    private long avgTicket;
}
