package com.stockshift.backend.api.mapper;

import com.stockshift.backend.api.dto.attribute.AttributeDefinitionResponse;
import com.stockshift.backend.api.dto.attribute.AttributeValueResponse;
import com.stockshift.backend.domain.attribute.AttributeDefinition;
import com.stockshift.backend.domain.attribute.AttributeValue;
import org.springframework.stereotype.Component;

@Component
public class AttributeMapper {

    public AttributeDefinitionResponse toDefinitionResponse(AttributeDefinition definition) {
        AttributeDefinitionResponse response = new AttributeDefinitionResponse();
        response.setId(definition.getId());
        response.setName(definition.getName());
        response.setDescription(definition.getDescription());
        response.setValueCount(definition.getValues() != null ? definition.getValues().size() : 0);
        response.setActive(definition.getActive());
        response.setCreatedAt(definition.getCreatedAt());
        response.setUpdatedAt(definition.getUpdatedAt());
        return response;
    }

    public AttributeValueResponse toValueResponse(AttributeValue value) {
        AttributeValueResponse response = new AttributeValueResponse();
        response.setId(value.getId());
        response.setDefinitionId(value.getDefinition().getId());
        response.setDefinitionName(value.getDefinition().getName());
        response.setValue(value.getValue());
        response.setDescription(value.getDescription());
        response.setActive(value.getActive());
        response.setCreatedAt(value.getCreatedAt());
        response.setUpdatedAt(value.getUpdatedAt());
        return response;
    }
}
