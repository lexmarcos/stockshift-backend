package br.com.stockshift.dto.internal.bot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotProductSearchResultResponse {
    private UUID productId;
    private String name;
    private String categoryName;
    private String imageUrl;
    private String barcode;
    private String sku;
    private UUID warehouseId;
    private String warehouseName;
    private BigDecimal totalQuantity;
    private Long latestBatchSellingPrice;
    private String latestBatchCode;
    private LocalDateTime latestBatchCreatedAt;
}
