package br.com.stockshift.dto.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID parentCategoryId;
    private String parentCategoryName;
    private Map<String, Object> attributesSchema;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
