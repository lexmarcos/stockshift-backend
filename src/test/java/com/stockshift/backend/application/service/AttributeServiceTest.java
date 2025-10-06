package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.attribute.CreateAttributeDefinitionRequest;
import com.stockshift.backend.api.dto.attribute.CreateAttributeValueRequest;
import com.stockshift.backend.api.dto.attribute.UpdateAttributeDefinitionRequest;
import com.stockshift.backend.api.dto.attribute.UpdateAttributeValueRequest;
import com.stockshift.backend.api.mapper.AttributeMapper;
import com.stockshift.backend.application.exception.InactiveAttributeException;
import com.stockshift.backend.domain.attribute.AttributeDefinition;
import com.stockshift.backend.domain.attribute.AttributeStatus;
import com.stockshift.backend.domain.attribute.AttributeType;
import com.stockshift.backend.domain.attribute.AttributeValue;
import com.stockshift.backend.domain.attribute.exception.AttributeDefinitionAlreadyExistsException;
import com.stockshift.backend.domain.attribute.exception.AttributeDefinitionNotFoundException;
import com.stockshift.backend.domain.attribute.exception.AttributeValueAlreadyExistsException;
import com.stockshift.backend.domain.attribute.exception.AttributeValueNotFoundException;
import com.stockshift.backend.infrastructure.repository.AttributeDefinitionRepository;
import com.stockshift.backend.infrastructure.repository.AttributeValueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttributeServiceTest {

    @Mock
    private AttributeDefinitionRepository definitionRepository;

    @Mock
    private AttributeValueRepository valueRepository;

    @Spy
    private AttributeMapper mapper = new AttributeMapper();

    @InjectMocks
    private AttributeService attributeService;

    private AttributeDefinition definition;
    private AttributeValue value;

    @BeforeEach
    void setUp() {
        definition = new AttributeDefinition();
        definition.setId(UUID.randomUUID());
        definition.setName("Size");
        definition.setCode("SIZE");
        definition.setType(AttributeType.ENUM);
        definition.setDescription("Size options");
        definition.setIsVariantDefining(true);
        definition.setIsRequired(false);
        definition.setValues(new ArrayList<>());
        definition.setStatus(AttributeStatus.ACTIVE);

        value = new AttributeValue();
        value.setId(UUID.randomUUID());
        value.setDefinition(definition);
        value.setValue("Large");
        value.setCode("LARGE");
        value.setDescription("Large size");
        value.setStatus(AttributeStatus.ACTIVE);
        definition.getValues().add(value);
    }

    @Test
    void shouldCreateDefinitionSuccessfully() {
        CreateAttributeDefinitionRequest request = new CreateAttributeDefinitionRequest();
        request.setName("Color");
        request.setCode("color");
        request.setType(AttributeType.ENUM);

        AttributeDefinition persisted = new AttributeDefinition();
        persisted.setId(UUID.randomUUID());

        when(definitionRepository.existsByCode("COLOR")).thenReturn(false);
        when(definitionRepository.save(any(AttributeDefinition.class))).thenReturn(persisted);

        AttributeDefinition result = attributeService.createDefinition(request);

        assertThat(result).isEqualTo(persisted);
        verify(definitionRepository).existsByCode("COLOR");
        verify(definitionRepository).save(any(AttributeDefinition.class));
        verify(mapper).toEntity(request);
    }

    @Test
    void shouldThrowWhenDefinitionCodeAlreadyExists() {
        CreateAttributeDefinitionRequest request = new CreateAttributeDefinitionRequest();
        request.setName("Color");
        request.setCode("color");
        request.setType(AttributeType.ENUM);

        when(definitionRepository.existsByCode("COLOR")).thenReturn(true);

        assertThatThrownBy(() -> attributeService.createDefinition(request))
                .isInstanceOf(AttributeDefinitionAlreadyExistsException.class)
                .hasMessageContaining("Definition with code color already exists");

        verify(definitionRepository).existsByCode("COLOR");
        verify(definitionRepository, never()).save(any(AttributeDefinition.class));
    }

    @Test
    void shouldReturnAllDefinitions() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<AttributeDefinition> page = new PageImpl<>(List.of(definition));
        when(definitionRepository.findAll(pageable)).thenReturn(page);

        Page<AttributeDefinition> result = attributeService.getAllDefinitions(pageable);

        assertThat(result.getContent()).containsExactly(definition);
        verify(definitionRepository).findAll(pageable);
    }

    @Test
    void shouldReturnActiveDefinitions() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<AttributeDefinition> page = new PageImpl<>(List.of(definition));
        when(definitionRepository.findAllByStatus(AttributeStatus.ACTIVE, pageable)).thenReturn(page);

        Page<AttributeDefinition> result = attributeService.getActiveDefinitions(pageable);

        assertThat(result.getContent()).containsExactly(definition);
        verify(definitionRepository).findAllByStatus(AttributeStatus.ACTIVE, pageable);
    }

    @Test
    void shouldGetDefinitionById() {
        UUID id = definition.getId();
        when(definitionRepository.findById(id)).thenReturn(Optional.of(definition));

        AttributeDefinition result = attributeService.getDefinitionById(id);

        assertThat(result).isEqualTo(definition);
        verify(definitionRepository).findById(id);
    }

    @Test
    void shouldThrowWhenDefinitionByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(definitionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attributeService.getDefinitionById(id))
                .isInstanceOf(AttributeDefinitionNotFoundException.class)
                .hasMessageContaining(id.toString());

        verify(definitionRepository).findById(id);
    }

    @Test
    void shouldGetDefinitionByCode() {
        when(definitionRepository.findByCode("SIZE")).thenReturn(Optional.of(definition));

        AttributeDefinition result = attributeService.getDefinitionByCode("size");

        assertThat(result).isEqualTo(definition);
        verify(definitionRepository).findByCode("SIZE");
    }

    @Test
    void shouldThrowWhenDefinitionByCodeNotFound() {
        when(definitionRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attributeService.getDefinitionByCode("unknown"))
                .isInstanceOf(AttributeDefinitionNotFoundException.class)
                .hasMessageContaining("Definition with code unknown not found");

        verify(definitionRepository).findByCode("UNKNOWN");
    }

    @Test
    void shouldUpdateDefinitionAndPersistChanges() {
        UUID id = definition.getId();
        UpdateAttributeDefinitionRequest request = new UpdateAttributeDefinitionRequest(
                "New Size", "Updated description", false, true, List.of(UUID.randomUUID()), 5
        );

        when(definitionRepository.findById(id)).thenReturn(Optional.of(definition));
        when(definitionRepository.save(definition)).thenReturn(definition);

        AttributeDefinition result = attributeService.updateDefinition(id, request);

        assertThat(result.getName()).isEqualTo("New Size");
        assertThat(result.getDescription()).isEqualTo("Updated description");
        assertThat(result.getIsVariantDefining()).isFalse();
        assertThat(result.getIsRequired()).isTrue();
        assertThat(result.getApplicableCategoryIds()).containsExactly(request.getApplicableCategoryIds().get(0));
        assertThat(result.getSortOrder()).isEqualTo(5);

        verify(mapper).updateEntity(request, definition);
        verify(definitionRepository).save(definition);
    }

    @Test
    void shouldDeactivateDefinitionAndValues() {
        UUID id = definition.getId();
        when(definitionRepository.findById(id)).thenReturn(Optional.of(definition));

        attributeService.deactivateDefinition(id);

        assertThat(definition.getStatus()).isEqualTo(AttributeStatus.INACTIVE);
        assertThat(value.getStatus()).isEqualTo(AttributeStatus.INACTIVE);
        verify(definitionRepository).save(definition);
    }

    @Test
    void shouldActivateDefinition() {
        UUID id = definition.getId();
        definition.setStatus(AttributeStatus.INACTIVE);
        when(definitionRepository.findById(id)).thenReturn(Optional.of(definition));
        when(definitionRepository.save(definition)).thenReturn(definition);

        attributeService.activateDefinition(id);

        assertThat(definition.getStatus()).isEqualTo(AttributeStatus.ACTIVE);
        verify(definitionRepository).save(definition);
    }

    @Test
    void shouldCreateValueSuccessfully() {
        UUID definitionId = definition.getId();
        CreateAttributeValueRequest request = new CreateAttributeValueRequest("Red", "red", "", "#FF0000");

        AttributeValue persisted = new AttributeValue();
        persisted.setId(UUID.randomUUID());

        when(definitionRepository.findById(definitionId)).thenReturn(Optional.of(definition));
        when(valueRepository.existsByDefinitionIdAndCode(definitionId, "RED")).thenReturn(false);
        when(valueRepository.save(any(AttributeValue.class))).thenReturn(persisted);

        AttributeValue result = attributeService.createValue(definitionId, request);

        assertThat(result).isEqualTo(persisted);
        verify(valueRepository).existsByDefinitionIdAndCode(definitionId, "RED");
        verify(valueRepository).save(any(AttributeValue.class));
        verify(mapper).toEntity(request, definition);
    }

    @Test
    void shouldThrowWhenCreatingValueForInactiveDefinition() {
        UUID definitionId = definition.getId();
        definition.setStatus(AttributeStatus.INACTIVE);
        CreateAttributeValueRequest request = new CreateAttributeValueRequest("Red", "red", null, null);

        when(definitionRepository.findById(definitionId)).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> attributeService.createValue(definitionId, request))
                .isInstanceOf(InactiveAttributeException.class)
                .hasMessageContaining("definition");

        verify(valueRepository, never()).save(any(AttributeValue.class));
    }

    @Test
    void shouldThrowWhenCreatingValueForNonEnumDefinition() {
        UUID definitionId = definition.getId();
        definition.setType(AttributeType.TEXT);
        CreateAttributeValueRequest request = new CreateAttributeValueRequest("Red", "red", null, null);

        when(definitionRepository.findById(definitionId)).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> attributeService.createValue(definitionId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Can only create values");

        verify(valueRepository, never()).save(any(AttributeValue.class));
    }

    @Test
    void shouldThrowWhenCreatingDuplicateValue() {
        UUID definitionId = definition.getId();
        CreateAttributeValueRequest request = new CreateAttributeValueRequest("Red", "red", null, null);

        when(definitionRepository.findById(definitionId)).thenReturn(Optional.of(definition));
        when(valueRepository.existsByDefinitionIdAndCode(definitionId, "RED")).thenReturn(true);

        assertThatThrownBy(() -> attributeService.createValue(definitionId, request))
                .isInstanceOf(AttributeValueAlreadyExistsException.class)
                .hasMessageContaining("Value with code RED already exists");

        verify(valueRepository).existsByDefinitionIdAndCode(definitionId, "RED");
        verify(valueRepository, never()).save(any(AttributeValue.class));
    }

    @Test
    void shouldGetValuesByDefinition() {
        UUID definitionId = definition.getId();
        Pageable pageable = PageRequest.of(0, 10);
        Page<AttributeValue> page = new PageImpl<>(List.of(value));

        when(definitionRepository.existsById(definitionId)).thenReturn(true);
        when(valueRepository.findAllByDefinitionId(definitionId, pageable)).thenReturn(page);

        Page<AttributeValue> result = attributeService.getValuesByDefinition(definitionId, pageable);

        assertThat(result.getContent()).containsExactly(value);
        verify(valueRepository).findAllByDefinitionId(definitionId, pageable);
    }

    @Test
    void shouldThrowWhenDefinitionMissingOnValuesQuery() {
        UUID definitionId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        when(definitionRepository.existsById(definitionId)).thenReturn(false);

        assertThatThrownBy(() -> attributeService.getValuesByDefinition(definitionId, pageable))
                .isInstanceOf(AttributeDefinitionNotFoundException.class)
                .hasMessageContaining(definitionId.toString());

        verify(valueRepository, never()).findAllByDefinitionId(any(), any());
    }

    @Test
    void shouldGetActiveValuesByDefinition() {
        UUID definitionId = definition.getId();
        Pageable pageable = PageRequest.of(0, 10);
        Page<AttributeValue> page = new PageImpl<>(List.of(value));

        when(definitionRepository.existsById(definitionId)).thenReturn(true);
        when(valueRepository.findAllByDefinitionIdAndStatus(definitionId, AttributeStatus.ACTIVE, pageable))
                .thenReturn(page);

        Page<AttributeValue> result = attributeService.getActiveValuesByDefinition(definitionId, pageable);

        assertThat(result.getContent()).containsExactly(value);
        verify(valueRepository).findAllByDefinitionIdAndStatus(definitionId, AttributeStatus.ACTIVE, pageable);
    }

    @Test
    void shouldThrowWhenDefinitionMissingOnActiveValuesQuery() {
        UUID definitionId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 5);

        when(definitionRepository.existsById(definitionId)).thenReturn(false);

        assertThatThrownBy(() -> attributeService.getActiveValuesByDefinition(definitionId, pageable))
                .isInstanceOf(AttributeDefinitionNotFoundException.class)
                .hasMessageContaining(definitionId.toString());

        verify(valueRepository, never()).findAllByDefinitionIdAndStatus(any(), any(), any());
    }

    @Test
    void shouldGetValueById() {
        UUID id = value.getId();
        when(valueRepository.findById(id)).thenReturn(Optional.of(value));

        AttributeValue result = attributeService.getValueById(id);

        assertThat(result).isEqualTo(value);
        verify(valueRepository).findById(id);
    }

    @Test
    void shouldThrowWhenValueByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(valueRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attributeService.getValueById(id))
                .isInstanceOf(AttributeValueNotFoundException.class)
                .hasMessageContaining(id.toString());

        verify(valueRepository).findById(id);
    }

    @Test
    void shouldGetValueByDefinitionAndCode() {
        UUID definitionId = definition.getId();
        when(valueRepository.findByDefinitionIdAndCode(definitionId, "LARGE"))
                .thenReturn(Optional.of(value));

        AttributeValue result = attributeService.getValueByDefinitionAndCode(definitionId, "large");

        assertThat(result).isEqualTo(value);
        verify(valueRepository).findByDefinitionIdAndCode(definitionId, "LARGE");
    }

    @Test
    void shouldThrowWhenValueByDefinitionAndCodeNotFound() {
        UUID definitionId = definition.getId();
        when(valueRepository.findByDefinitionIdAndCode(definitionId, "BLUE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> attributeService.getValueByDefinitionAndCode(definitionId, "blue"))
                .isInstanceOf(AttributeValueNotFoundException.class)
                .hasMessageContaining("Value with code blue not found");

        verify(valueRepository).findByDefinitionIdAndCode(definitionId, "BLUE");
    }

    @Test
    void shouldUpdateValueAndPersistChanges() {
        UUID id = value.getId();
        UpdateAttributeValueRequest request = new UpdateAttributeValueRequest("Medium", "Desc", "#00FF00");

        when(valueRepository.findById(id)).thenReturn(Optional.of(value));
        when(valueRepository.save(value)).thenReturn(value);

        AttributeValue result = attributeService.updateValue(id, request);

        assertThat(result.getValue()).isEqualTo("Medium");
        assertThat(result.getDescription()).isEqualTo("Desc");
        assertThat(result.getSwatchHex()).isEqualTo("#00FF00");
        verify(mapper).updateEntity(request, value);
        verify(valueRepository).save(value);
    }

    @Test
    void shouldDeactivateValue() {
        UUID id = value.getId();
        when(valueRepository.findById(id)).thenReturn(Optional.of(value));

        attributeService.deactivateValue(id);

        ArgumentCaptor<AttributeValue> captor = ArgumentCaptor.forClass(AttributeValue.class);
        verify(valueRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AttributeStatus.INACTIVE);
    }

    @Test
    void shouldActivateValue() {
        UUID id = value.getId();
        value.setStatus(AttributeStatus.INACTIVE);
        when(valueRepository.findById(id)).thenReturn(Optional.of(value));

        attributeService.activateValue(id);

        ArgumentCaptor<AttributeValue> captor = ArgumentCaptor.forClass(AttributeValue.class);
        verify(valueRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AttributeStatus.ACTIVE);
    }
}
