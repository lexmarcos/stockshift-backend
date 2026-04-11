package br.com.stockshift.dto.tenant;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfinitePayConfigResponse {
    private String handle;
    private String docNumber;
    private boolean configured;
}
