package com.stockshift.backend.api.dto.attribute;

import com.stockshift.backend.domain.attribute.AttributeStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttributeValueResponse {

    private UUID id;
    private UUID definitionId;
    private String definitionName;
    private String definitionCode;
    private String value;
    private String code;
    private String description;
    private String swatchHex;
    private AttributeStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
