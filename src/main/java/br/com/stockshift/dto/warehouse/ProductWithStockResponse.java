package br.com.stockshift.dto.warehouse;

import br.com.stockshift.dto.brand.BrandResponse;
import br.com.stockshift.model.enums.BarcodeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductWithStockResponse {
    private UUID id;
    private String name;
    private String sku;
    private String barcode;
    private BarcodeType barcodeType;
    private String description;
    private UUID categoryId;
    private String categoryName;
    private BrandResponse brand;
    private Boolean isKit;
    private Map<String, Object> attributes;
    private Boolean hasExpiration;
    private Boolean active;
    private Long totalQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
