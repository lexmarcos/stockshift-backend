package br.com.stockshift.dto.productprompt;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductPromptCompanyAssetsResponse {
    private String logoUrl;
}
