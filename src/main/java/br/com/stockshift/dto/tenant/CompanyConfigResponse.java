package br.com.stockshift.dto.tenant;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyConfigResponse {
    private String businessName;
    private String document;
    private String email;
    private String phone;
    private String logoUrl;
    private boolean isActive;
}
