package br.com.stockshift.dto.sale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfinitePayConfirmResponse {
    private String status;
    private String saleId;
    private String message;
}
