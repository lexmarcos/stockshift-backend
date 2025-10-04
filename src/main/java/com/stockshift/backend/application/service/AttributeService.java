package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.attribute.CreateAttributeDefinitionRequest;
import com.stockshift.backend.api.dto.attribute.CreateAttributeValueRequest;
import com.stockshift.backend.api.dto.attribute.UpdateAttributeDefinitionRequest;
import com.stockshift.backend.api.dto.attribute.UpdateAttributeValueRequest;
import com.stockshift.backend.api.mapper.AttributeMapper;
import com.stockshift.backend.application.exception.InactiveAttributeException;
import com.stockshift.backend.domain.attribute.AttributeDefinition;
import com.stockshift.backend.domain.attribute.AttributeStatus;
import com.stockshift.backend.domain.attribute.AttributeValue;
import com.stockshift.backend.domain.attribute.exception.AttributeDefinitionAlreadyExistsException;
import com.stockshift.backend.domain.attribute.exception.AttributeDefinitionNotFoundException;
import com.stockshift.backend.domain.attribute.exception.AttributeValueAlreadyExistsException;
import com.stockshift.backend.domain.attribute.exception.AttributeValueNotFoundException;
import com.stockshift.backend.infrastructure.repository.AttributeDefinitionRepository;
import com.stockshift.backend.infrastructure.repository.AttributeValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttributeService {

    private final AttributeDefinitionRepository definitionRepository;
    private final AttributeValueRepository valueRepository;
    private final AttributeMapper mapper;

    // Attribute Definition Methods

    @Transactional
    public AttributeDefinition createDefinition(CreateAttributeDefinitionRequest request) {
        if (definitionRepository.existsByCode(request.getCode().toUpperCase())) {
            throw new AttributeDefinitionAlreadyExistsException("Definition with code " + request.getCode() + " already exists");
        }

        AttributeDefinition definition = mapper.toEntity(request);
        return definitionRepository.save(definition);
    }

    @Transactional(readOnly = true)
    public Page<AttributeDefinition> getAllDefinitions(Pageable pageable) {
        return definitionRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AttributeDefinition> getActiveDefinitions(Pageable pageable) {
        return definitionRepository.findAllByStatus(AttributeStatus.ACTIVE, pageable);
    }

    @Transactional(readOnly = true)
    public AttributeDefinition getDefinitionById(UUID id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new AttributeDefinitionNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public AttributeDefinition getDefinitionByCode(String code) {
        return definitionRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new AttributeDefinitionNotFoundException("Definition with code " + code + " not found"));
    }

    @Transactional
    public AttributeDefinition updateDefinition(UUID id, UpdateAttributeDefinitionRequest request) {
        AttributeDefinition definition = getDefinitionById(id);
        mapper.updateEntity(request, definition);
        return definitionRepository.save(definition);
    }

    @Transactional
    public void deactivateDefinition(UUID id) {
        AttributeDefinition definition = getDefinitionById(id);
        definition.setStatus(AttributeStatus.INACTIVE);
        
        // Also deactivate all values
        definition.getValues().forEach(value -> value.setStatus(AttributeStatus.INACTIVE));
        
        definitionRepository.save(definition);
    }

    @Transactional
    public void activateDefinition(UUID id) {
        AttributeDefinition definition = getDefinitionById(id);
        definition.setStatus(AttributeStatus.ACTIVE);
        definitionRepository.save(definition);
    }

    // Attribute Value Methods

    @Transactional
    public AttributeValue createValue(UUID definitionId, CreateAttributeValueRequest request) {
        AttributeDefinition definition = getDefinitionById(definitionId);
        
        if (definition.getStatus() == AttributeStatus.INACTIVE) {
            throw new InactiveAttributeException("definition", definition.getCode());
        }

        if (!definition.isEnumType()) {
            throw new IllegalArgumentException("Can only create values for ENUM or MULTI_ENUM attribute definitions");
        }

        String code = request.getCode().toUpperCase();
        if (valueRepository.existsByDefinitionIdAndCode(definitionId, code)) {
            throw new AttributeValueAlreadyExistsException("Value with code " + code + " already exists for definition " + definition.getCode());
        }

        AttributeValue value = mapper.toEntity(request, definition);
        return valueRepository.save(value);
    }

    @Transactional(readOnly = true)
    public Page<AttributeValue> getValuesByDefinition(UUID definitionId, Pageable pageable) {
        if (!definitionRepository.existsById(definitionId)) {
            throw new AttributeDefinitionNotFoundException(definitionId);
        }
        return valueRepository.findAllByDefinitionId(definitionId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AttributeValue> getActiveValuesByDefinition(UUID definitionId, Pageable pageable) {
        if (!definitionRepository.existsById(definitionId)) {
            throw new AttributeDefinitionNotFoundException(definitionId);
        }
        return valueRepository.findAllByDefinitionIdAndStatus(definitionId, AttributeStatus.ACTIVE, pageable);
    }

    @Transactional(readOnly = true)
    public AttributeValue getValueById(UUID id) {
        return valueRepository.findById(id)
                .orElseThrow(() -> new AttributeValueNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public AttributeValue getValueByDefinitionAndCode(UUID definitionId, String code) {
        return valueRepository.findByDefinitionIdAndCode(definitionId, code.toUpperCase())
                .orElseThrow(() -> new AttributeValueNotFoundException("Value with code " + code + " not found"));
    }

    @Transactional
    public AttributeValue updateValue(UUID id, UpdateAttributeValueRequest request) {
        AttributeValue value = getValueById(id);
        mapper.updateEntity(request, value);
        return valueRepository.save(value);
    }

    @Transactional
    public void deactivateValue(UUID id) {
        AttributeValue value = getValueById(id);
        value.setStatus(AttributeStatus.INACTIVE);
        valueRepository.save(value);
    }

    @Transactional
    public void activateValue(UUID id) {
        AttributeValue value = getValueById(id);
        value.setStatus(AttributeStatus.ACTIVE);
        valueRepository.save(value);
    }
}
