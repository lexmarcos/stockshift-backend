package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.attribute.*;
import com.stockshift.backend.api.mapper.AttributeMapper;
import com.stockshift.backend.application.service.AttributeService;
import com.stockshift.backend.domain.attribute.AttributeDefinition;
import com.stockshift.backend.domain.attribute.AttributeValue;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attributes")
@RequiredArgsConstructor
public class AttributeController {

    private final AttributeService attributeService;
    private final AttributeMapper attributeMapper;

    // Attribute Definition Endpoints

    @PostMapping("/definitions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AttributeDefinitionResponse> createDefinition(
            @Valid @RequestBody CreateAttributeDefinitionRequest request
    ) {
        AttributeDefinition definition = attributeService.createDefinition(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attributeMapper.toDefinitionResponse(definition));
    }

    @GetMapping("/definitions")
    public ResponseEntity<Page<AttributeDefinitionResponse>> getAllDefinitions(
            @RequestParam(value = "onlyActive", required = false, defaultValue = "false") Boolean onlyActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<AttributeDefinition> definitions = onlyActive
                ? attributeService.getActiveDefinitions(pageable)
                : attributeService.getAllDefinitions(pageable);
        return ResponseEntity.ok(definitions.map(attributeMapper::toDefinitionResponse));
    }

    @GetMapping("/definitions/{id}")
    public ResponseEntity<AttributeDefinitionResponse> getDefinitionById(
            @PathVariable(value = "id") UUID id
    ) {
        AttributeDefinition definition = attributeService.getDefinitionById(id);
        return ResponseEntity.ok(attributeMapper.toDefinitionResponse(definition));
    }

    @GetMapping("/definitions/code/{code}")
    public ResponseEntity<AttributeDefinitionResponse> getDefinitionByCode(
            @PathVariable(value = "code") String code
    ) {
        AttributeDefinition definition = attributeService.getDefinitionByCode(code);
        return ResponseEntity.ok(attributeMapper.toDefinitionResponse(definition));
    }

    @PutMapping("/definitions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AttributeDefinitionResponse> updateDefinition(
            @PathVariable(value = "id") UUID id,
            @Valid @RequestBody UpdateAttributeDefinitionRequest request
    ) {
        AttributeDefinition definition = attributeService.updateDefinition(id, request);
        return ResponseEntity.ok(attributeMapper.toDefinitionResponse(definition));
    }

    @DeleteMapping("/definitions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDefinition(@PathVariable(value = "id") UUID id) {
        attributeService.deactivateDefinition(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/definitions/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AttributeDefinitionResponse> activateDefinition(
            @PathVariable(value = "id") UUID id
    ) {
        attributeService.activateDefinition(id);
        AttributeDefinition definition = attributeService.getDefinitionById(id);
        return ResponseEntity.ok(attributeMapper.toDefinitionResponse(definition));
    }

    // Attribute Value Endpoints

    @PostMapping("/definitions/{definitionId}/values")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AttributeValueResponse> createValue(
            @PathVariable(value = "definitionId") UUID definitionId,
            @Valid @RequestBody CreateAttributeValueRequest request
    ) {
        AttributeValue value = attributeService.createValue(definitionId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attributeMapper.toValueResponse(value));
    }

    @GetMapping("/definitions/{definitionId}/values")
    public ResponseEntity<Page<AttributeValueResponse>> getValuesByDefinition(
            @PathVariable(value = "definitionId") UUID definitionId,
            @RequestParam(value = "onlyActive", required = false, defaultValue = "false") Boolean onlyActive,
            @PageableDefault(size = 20, sort = "value", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<AttributeValue> values = onlyActive 
            ? attributeService.getActiveValuesByDefinition(definitionId, pageable)
            : attributeService.getValuesByDefinition(definitionId, pageable);
        return ResponseEntity.ok(values.map(attributeMapper::toValueResponse));
    }

    @GetMapping("/values/{id}")
    public ResponseEntity<AttributeValueResponse> getValueById(
            @PathVariable(value = "id") UUID id
    ) {
        AttributeValue value = attributeService.getValueById(id);
        return ResponseEntity.ok(attributeMapper.toValueResponse(value));
    }

    @PutMapping("/values/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AttributeValueResponse> updateValue(
            @PathVariable(value = "id") UUID id,
            @Valid @RequestBody UpdateAttributeValueRequest request
    ) {
        AttributeValue value = attributeService.updateValue(id, request);
        return ResponseEntity.ok(attributeMapper.toValueResponse(value));
    }

    @DeleteMapping("/values/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteValue(@PathVariable(value = "id") UUID id) {
        attributeService.deactivateValue(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/values/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AttributeValueResponse> activateValue(
            @PathVariable(value = "id") UUID id
    ) {
        attributeService.activateValue(id);
        AttributeValue value = attributeService.getValueById(id);
        return ResponseEntity.ok(attributeMapper.toValueResponse(value));
    }
}
