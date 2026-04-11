package br.com.stockshift.dto.sale;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NextSaleCodeResponse {
    private String code;
}
