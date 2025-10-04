package com.stockshift.backend.api.dto.attribute;

import com.stockshift.backend.domain.attribute.AttributeStatus;
import com.stockshift.backend.domain.attribute.AttributeType;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class AttributeDefinitionResponse {
    private UUID id;
    private String name;
    private String code;
    private AttributeType type;
    private String description;
    private Boolean isVariantDefining;
    private Boolean isRequired;
    private List<UUID> applicableCategoryIds;
    private Integer sortOrder;
    private AttributeStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
