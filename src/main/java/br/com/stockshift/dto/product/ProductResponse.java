package br.com.stockshift.dto.product;

import br.com.stockshift.model.enums.BarcodeType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID categoryId;
    private String categoryName;
    private String barcode;
    private BarcodeType barcodeType;
    private String sku;
    private Boolean isKit;
    private JsonNode attributes;
    private Boolean hasExpiration;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
