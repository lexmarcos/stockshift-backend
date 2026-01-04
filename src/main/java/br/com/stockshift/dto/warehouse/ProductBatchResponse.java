package br.com.stockshift.dto.warehouse;

import br.com.stockshift.dto.product.ProductResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatchResponse {
    private ProductResponse product;
    private BatchResponse batch;
}
