package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.attribute.AttributeDefinitionResponse;
import com.stockshift.backend.api.dto.attribute.AttributeValueResponse;
import com.stockshift.backend.api.dto.attribute.CreateAttributeDefinitionRequest;
import com.stockshift.backend.api.dto.attribute.CreateAttributeValueRequest;
import com.stockshift.backend.api.dto.attribute.UpdateAttributeDefinitionRequest;
import com.stockshift.backend.api.dto.attribute.UpdateAttributeValueRequest;
import com.stockshift.backend.api.mapper.AttributeMapper;
import com.stockshift.backend.application.service.AttributeService;
import com.stockshift.backend.domain.attribute.AttributeDefinition;
import com.stockshift.backend.domain.attribute.AttributeStatus;
import com.stockshift.backend.domain.attribute.AttributeType;
import com.stockshift.backend.domain.attribute.AttributeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributeControllerTest {

    @Mock
    private AttributeService attributeService;

    @Mock
    private AttributeMapper attributeMapper;

    @InjectMocks
    private AttributeController attributeController;

    private AttributeDefinition definition;
    private AttributeDefinitionResponse definitionResponse;
    private AttributeValue value;
    private AttributeValueResponse valueResponse;

    @BeforeEach
    void setUp() {
        definition = new AttributeDefinition();
        definition.setId(UUID.randomUUID());
        definition.setName("Color");
        definition.setCode("COLOR");
        definition.setType(AttributeType.ENUM);
        definition.setStatus(AttributeStatus.ACTIVE);

        definitionResponse = new AttributeDefinitionResponse();
        definitionResponse.setId(definition.getId());
        definitionResponse.setName(definition.getName());
        definitionResponse.setCode(definition.getCode());
        definitionResponse.setType(AttributeType.ENUM);

        value = new AttributeValue();
        value.setId(UUID.randomUUID());
        value.setValue("Red");
        value.setCode("RED");

        valueResponse = new AttributeValueResponse(
                value.getId(),
                definition.getId(),
                definition.getName(),
                definition.getCode(),
                value.getValue(),
                value.getCode(),
                null,
                null,
                AttributeStatus.ACTIVE,
                null,
                null
        );
    }

    @Test
    void createDefinitionShouldReturnCreatedResponse() {
        CreateAttributeDefinitionRequest request = new CreateAttributeDefinitionRequest();
        when(attributeService.createDefinition(request)).thenReturn(definition);
        when(attributeMapper.toDefinitionResponse(definition)).thenReturn(definitionResponse);

        ResponseEntity<AttributeDefinitionResponse> response = attributeController.createDefinition(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(definitionResponse);
    }

    @Test
    void getAllDefinitionsShouldUseActiveServiceWhenRequested() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AttributeDefinition> page = new PageImpl<>(List.of(definition));
        when(attributeService.getActiveDefinitions(pageable)).thenReturn(page);
        when(attributeMapper.toDefinitionResponse(definition)).thenReturn(definitionResponse);

        ResponseEntity<Page<AttributeDefinitionResponse>> response = attributeController.getAllDefinitions(true, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(definitionResponse);
        verify(attributeService).getActiveDefinitions(pageable);
    }

    @Test
    void createValueShouldReturnCreatedResponse() {
        CreateAttributeValueRequest request = new CreateAttributeValueRequest("Red", "RED", null, null);
        UUID definitionId = UUID.randomUUID();
        when(attributeService.createValue(definitionId, request)).thenReturn(value);
        when(attributeMapper.toValueResponse(value)).thenReturn(valueResponse);

        ResponseEntity<AttributeValueResponse> response = attributeController.createValue(definitionId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(valueResponse);
    }

    @Test
    void activateValueShouldReturnUpdatedValue() {
        UUID id = UUID.randomUUID();
        when(attributeService.getValueById(id)).thenReturn(value);
        when(attributeMapper.toValueResponse(value)).thenReturn(valueResponse);

        ResponseEntity<AttributeValueResponse> response = attributeController.activateValue(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(valueResponse);
        verify(attributeService).activateValue(id);
        verify(attributeService).getValueById(id);
    }
}
