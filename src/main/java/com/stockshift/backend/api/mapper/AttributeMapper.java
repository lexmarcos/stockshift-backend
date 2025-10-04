package com.stockshift.backend.api.mapper;

import com.stockshift.backend.api.dto.attribute.*;
import com.stockshift.backend.domain.attribute.AttributeDefinition;
import com.stockshift.backend.domain.attribute.AttributeStatus;
import com.stockshift.backend.domain.attribute.AttributeValue;
import org.springframework.stereotype.Component;

@Component
public class AttributeMapper {

    public AttributeDefinition toEntity(CreateAttributeDefinitionRequest request) {
        AttributeDefinition definition = new AttributeDefinition();
        definition.setName(request.getName());
        definition.setCode(request.getCode().toUpperCase());
        definition.setType(request.getType());
        definition.setDescription(request.getDescription());
        definition.setIsVariantDefining(request.getIsVariantDefining() != null ? request.getIsVariantDefining() : true);
        definition.setIsRequired(request.getIsRequired() != null ? request.getIsRequired() : false);
        definition.setApplicableCategoryIds(request.getApplicableCategoryIds());
        definition.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        definition.setStatus(AttributeStatus.ACTIVE);
        return definition;
    }

    public void updateEntity(UpdateAttributeDefinitionRequest request, AttributeDefinition definition) {
        if (request.getName() != null) {
            definition.setName(request.getName());
        }
        if (request.getDescription() != null) {
            definition.setDescription(request.getDescription());
        }
        if (request.getIsVariantDefining() != null) {
            definition.setIsVariantDefining(request.getIsVariantDefining());
        }
        if (request.getIsRequired() != null) {
            definition.setIsRequired(request.getIsRequired());
        }
        if (request.getApplicableCategoryIds() != null) {
            definition.setApplicableCategoryIds(request.getApplicableCategoryIds());
        }
        if (request.getSortOrder() != null) {
            definition.setSortOrder(request.getSortOrder());
        }
    }

    public AttributeDefinitionResponse toResponse(AttributeDefinition definition) {
        AttributeDefinitionResponse response = new AttributeDefinitionResponse();
        response.setId(definition.getId());
        response.setName(definition.getName());
        response.setCode(definition.getCode());
        response.setType(definition.getType());
        response.setDescription(definition.getDescription());
        response.setIsVariantDefining(definition.getIsVariantDefining());
        response.setIsRequired(definition.getIsRequired());
        response.setApplicableCategoryIds(definition.getApplicableCategoryIds());
        response.setSortOrder(definition.getSortOrder());
        response.setStatus(definition.getStatus());
        response.setCreatedAt(definition.getCreatedAt());
        response.setUpdatedAt(definition.getUpdatedAt());
        return response;
    }

    public AttributeValue toEntity(CreateAttributeValueRequest request, AttributeDefinition definition) {
        AttributeValue value = new AttributeValue();
        value.setDefinition(definition);
        value.setValue(request.getValue());
        value.setCode(request.getCode().toUpperCase());
        value.setDescription(request.getDescription());
        value.setSwatchHex(request.getSwatchHex());
        value.setStatus(AttributeStatus.ACTIVE);
        return value;
    }

    public void updateEntity(UpdateAttributeValueRequest request, AttributeValue value) {
        if (request.getValue() != null) {
            value.setValue(request.getValue());
        }
        if (request.getDescription() != null) {
            value.setDescription(request.getDescription());
        }
        if (request.getSwatchHex() != null) {
            value.setSwatchHex(request.getSwatchHex());
        }
    }

    public AttributeValueResponse toResponse(AttributeValue value) {
        AttributeValueResponse response = new AttributeValueResponse();
        response.setId(value.getId());
        response.setDefinitionId(value.getDefinition().getId());
        response.setDefinitionName(value.getDefinition().getName());
        response.setDefinitionCode(value.getDefinition().getCode());
        response.setValue(value.getValue());
        response.setCode(value.getCode());
        response.setDescription(value.getDescription());
        response.setSwatchHex(value.getSwatchHex());
        response.setStatus(value.getStatus());
        response.setCreatedAt(value.getCreatedAt());
        response.setUpdatedAt(value.getUpdatedAt());
        return response;
    }

    // Legacy methods for backward compatibility
    public AttributeDefinitionResponse toDefinitionResponse(AttributeDefinition definition) {
        return toResponse(definition);
    }

    public AttributeValueResponse toValueResponse(AttributeValue value) {
        return toResponse(value);
    }
}
