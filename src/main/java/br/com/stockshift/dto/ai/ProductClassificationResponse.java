package br.com.stockshift.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductClassificationResponse {
    private String name;

    // Brand info
    private UUID brandId;
    private String brandName;

    // Category info
    private UUID categoryId;
    private String categoryName;

    // Volume/Attributes
    private Double volumeValue;
    private String volumeUnit;

    // Fallback info if no match found in DB
    private String detectedCategory;
    private String detectedBrand;
}
