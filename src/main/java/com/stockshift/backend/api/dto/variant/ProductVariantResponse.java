package com.stockshift.backend.api.dto.variant;

import com.stockshift.backend.domain.attribute.AttributeType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String sku;
    private String gtin;
    private String attributesHash;
    private List<VariantAttributeInfo> attributes;
    private Long price;
    private Long effectivePrice;
    private Integer weight;
    private Integer length;
    private Integer width;
    private Integer height;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantAttributeInfo {
        private UUID valueId;
        private String value;
        private String valueCode;
        private UUID definitionId;
        private String definitionName;
        private String definitionCode;
        private AttributeType definitionType;
        private Boolean isVariantDefining;
    }
}
