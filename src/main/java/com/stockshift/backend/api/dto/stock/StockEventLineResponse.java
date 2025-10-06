package com.stockshift.backend.api.dto.stock;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockEventLineResponse {
    private UUID id;
    private UUID variantId;
    private String variantSku;
    private Long quantity;
}
