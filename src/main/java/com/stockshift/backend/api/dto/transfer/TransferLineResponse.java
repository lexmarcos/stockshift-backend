package com.stockshift.backend.api.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferLineResponse {
    private UUID id;
    private UUID variantId;
    private String variantSku;
    private Long quantity;
}
