package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.attribute.CreateAttributeDefinitionRequest;
import com.stockshift.backend.api.dto.attribute.CreateAttributeValueRequest;
import com.stockshift.backend.api.dto.attribute.UpdateAttributeDefinitionRequest;
import com.stockshift.backend.api.dto.attribute.UpdateAttributeValueRequest;
import com.stockshift.backend.domain.attribute.AttributeDefinition;
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

    // Attribute Definition Methods

    @Transactional
    public AttributeDefinition createDefinition(CreateAttributeDefinitionRequest request) {
        if (definitionRepository.existsByNameAndActiveTrue(request.getName())) {
            throw new AttributeDefinitionAlreadyExistsException(request.getName());
        }

        AttributeDefinition definition = new AttributeDefinition();
        definition.setName(request.getName());
        definition.setDescription(request.getDescription());

        return definitionRepository.save(definition);
    }

    @Transactional(readOnly = true)
    public Page<AttributeDefinition> getAllDefinitions(Pageable pageable) {
        return definitionRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AttributeDefinition> getActiveDefinitions(Pageable pageable) {
        return definitionRepository.findAllByActiveTrue(pageable);
    }

    @Transactional(readOnly = true)
    public AttributeDefinition getDefinitionById(UUID id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new AttributeDefinitionNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public AttributeDefinition getDefinitionByName(String name) {
        return definitionRepository.findByNameAndActiveTrue(name)
                .orElseThrow(() -> new AttributeDefinitionNotFoundException(name));
    }

    @Transactional
    public AttributeDefinition updateDefinition(UUID id, UpdateAttributeDefinitionRequest request) {
        AttributeDefinition definition = getDefinitionById(id);

        if (request.getName() != null && !request.getName().equals(definition.getName())) {
            if (definitionRepository.existsByNameAndActiveTrueAndIdNot(request.getName(), id)) {
                throw new AttributeDefinitionAlreadyExistsException(request.getName());
            }
            definition.setName(request.getName());
        }

        if (request.getDescription() != null) {
            definition.setDescription(request.getDescription());
        }

        return definitionRepository.save(definition);
    }

    @Transactional
    public void deleteDefinition(UUID id) {
        AttributeDefinition definition = getDefinitionById(id);
        definition.setActive(false);
        
        // Also deactivate all values
        definition.getValues().forEach(value -> value.setActive(false));
        
        definitionRepository.save(definition);
    }

    @Transactional
    public void activateDefinition(UUID id) {
        AttributeDefinition definition = getDefinitionById(id);
        definition.setActive(true);
        definitionRepository.save(definition);
    }

    // Attribute Value Methods

    @Transactional
    public AttributeValue createValue(UUID definitionId, CreateAttributeValueRequest request) {
        AttributeDefinition definition = getDefinitionById(definitionId);

        if (!definition.getActive()) {
            throw new IllegalArgumentException("Cannot add values to inactive attribute definition");
        }

        if (valueRepository.existsByDefinitionIdAndValueAndActiveTrue(definitionId, request.getValue())) {
            throw new AttributeValueAlreadyExistsException(definition.getName(), request.getValue());
        }

        AttributeValue value = new AttributeValue();
        value.setDefinition(definition);
        value.setValue(request.getValue());
        value.setDescription(request.getDescription());

        return valueRepository.save(value);
    }

    @Transactional(readOnly = true)
    public Page<AttributeValue> getValuesByDefinition(UUID definitionId, Boolean onlyActive, Pageable pageable) {
        // Verify definition exists
        if (!definitionRepository.existsById(definitionId)) {
            throw new AttributeDefinitionNotFoundException(definitionId);
        }

        return onlyActive 
            ? valueRepository.findByDefinitionIdAndActiveTrue(definitionId, pageable)
            : valueRepository.findByDefinitionId(definitionId, pageable);
    }

    @Transactional(readOnly = true)
    public AttributeValue getValueById(UUID id) {
        return valueRepository.findById(id)
                .orElseThrow(() -> new AttributeValueNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public AttributeValue getValueByDefinitionAndValue(UUID definitionId, String value) {
        return valueRepository.findByDefinitionIdAndValueAndActiveTrue(definitionId, value)
                .orElseThrow(() -> new AttributeValueNotFoundException(definitionId, value));
    }

    @Transactional
    public AttributeValue updateValue(UUID id, UpdateAttributeValueRequest request) {
        AttributeValue value = getValueById(id);

        if (request.getValue() != null && !request.getValue().equals(value.getValue())) {
            if (valueRepository.existsByDefinitionIdAndValueAndActiveTrueAndIdNot(
                    value.getDefinition().getId(), request.getValue(), id)) {
                throw new AttributeValueAlreadyExistsException(
                    value.getDefinition().getName(), request.getValue());
            }
            value.setValue(request.getValue());
        }

        if (request.getDescription() != null) {
            value.setDescription(request.getDescription());
        }

        return valueRepository.save(value);
    }

    @Transactional
    public void deleteValue(UUID id) {
        AttributeValue value = getValueById(id);
        value.setActive(false);
        valueRepository.save(value);
    }

    @Transactional
    public void activateValue(UUID id) {
        AttributeValue value = getValueById(id);
        
        if (!value.getDefinition().getActive()) {
            throw new IllegalArgumentException("Cannot activate value: definition is not active");
        }
        
        value.setActive(true);
        valueRepository.save(value);
    }
}
