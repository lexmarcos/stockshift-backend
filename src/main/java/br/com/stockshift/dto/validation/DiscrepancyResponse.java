package br.com.stockshift.dto.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyResponse {
    private UUID productId;
    private String productName;
    private Integer expected;
    private Integer received;
    private Integer missing;
}
