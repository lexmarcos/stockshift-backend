package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.variant.CreateProductVariantRequest;
import com.stockshift.backend.api.dto.variant.UpdateProductVariantRequest;
import com.stockshift.backend.api.mapper.ProductVariantMapper;
import com.stockshift.backend.application.exception.*;
import com.stockshift.backend.application.util.AttributeHashCalculator;
import com.stockshift.backend.domain.attribute.AttributeDefinition;
import com.stockshift.backend.domain.attribute.AttributeStatus;
import com.stockshift.backend.domain.attribute.AttributeValue;
import com.stockshift.backend.domain.category.Category;
import com.stockshift.backend.domain.product.Product;
import com.stockshift.backend.domain.product.ProductVariant;
import com.stockshift.backend.domain.product.exception.ProductNotFoundException;
import com.stockshift.backend.domain.product.exception.ProductVariantNotFoundException;
import com.stockshift.backend.infrastructure.repository.AttributeDefinitionRepository;
import com.stockshift.backend.infrastructure.repository.AttributeValueRepository;
import com.stockshift.backend.infrastructure.repository.ProductRepository;
import com.stockshift.backend.infrastructure.repository.ProductVariantRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AttributeDefinitionRepository definitionRepository;

    @Mock
    private AttributeValueRepository valueRepository;

    @Mock
    private ProductVariantMapper mapper;

    @Mock
    private AttributeHashCalculator hashCalculator;

    @InjectMocks
    private ProductVariantService productVariantService;

    private Product testProduct;
    private ProductVariant testVariant;
    private Category testCategory;
    private AttributeDefinition testDefinition;
    private AttributeValue testValue;
    private CreateProductVariantRequest createRequest;
    private UpdateProductVariantRequest updateRequest;

    @BeforeEach
    void setUp() {
        testCategory = new Category();
        testCategory.setId(UUID.randomUUID());
        testCategory.setName("Test Category");
        testCategory.setActive(true);

        testProduct = new Product();
        testProduct.setId(UUID.randomUUID());
        testProduct.setName("Test Product");
        testProduct.setCategory(testCategory);
        testProduct.setActive(true);

        testDefinition = new AttributeDefinition();
        testDefinition.setId(UUID.randomUUID());
        testDefinition.setCode("COLOR");
        testDefinition.setStatus(AttributeStatus.ACTIVE);
        testDefinition.setIsVariantDefining(true);
        testDefinition.setIsRequired(false);
        testDefinition.setApplicableCategoryIds(new ArrayList<>());

        testValue = new AttributeValue();
        testValue.setId(UUID.randomUUID());
        testValue.setCode("RED");
        testValue.setValue("Red");
        testValue.setStatus(AttributeStatus.ACTIVE);
        testValue.setDefinition(testDefinition);

        testVariant = new ProductVariant();
        testVariant.setId(UUID.randomUUID());
        testVariant.setSku("TEST-SKU-001");
        testVariant.setGtin("1234567890123");
        testVariant.setProduct(testProduct);
        testVariant.setActive(true);
        testVariant.setAttributes(new ArrayList<>());

        createRequest = new CreateProductVariantRequest();
        createRequest.setSku("NEW-SKU-001");
        createRequest.setGtin("9876543210987");
        createRequest.setPrice(10000L);
        List<CreateProductVariantRequest.VariantAttributePair> attributes = new ArrayList<>();
        attributes.add(new CreateProductVariantRequest.VariantAttributePair(
                testDefinition.getId(),
                testValue.getId()
        ));
        createRequest.setAttributes(attributes);

        updateRequest = new UpdateProductVariantRequest();
        updateRequest.setGtin("1111111111111");
        updateRequest.setPrice(15000L);
    }

    @Test
    void shouldCreateVariantSuccessfully() {
        // Given
        UUID productId = testProduct.getId();
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(variantRepository.existsBySku(createRequest.getSku())).thenReturn(false);
        when(variantRepository.existsByGtin(createRequest.getGtin())).thenReturn(false);
        when(definitionRepository.findById(testDefinition.getId())).thenReturn(Optional.of(testDefinition));
        when(valueRepository.findById(testValue.getId())).thenReturn(Optional.of(testValue));
        when(definitionRepository.findAll()).thenReturn(List.of(testDefinition));
        when(hashCalculator.calculateHash(anyList())).thenReturn("test-hash");
        when(variantRepository.existsByProductIdAndAttributesHash(productId, "test-hash")).thenReturn(false);
        when(mapper.toEntity(createRequest)).thenReturn(testVariant);
        when(variantRepository.save(any(ProductVariant.class))).thenReturn(testVariant);

        // When
        ProductVariant createdVariant = productVariantService.createVariant(productId, createRequest);

        // Then
        assertThat(createdVariant).isNotNull();
        verify(productRepository).findById(productId);
        verify(variantRepository).existsBySku(createRequest.getSku());
        verify(variantRepository, times(2)).save(any(ProductVariant.class));
    }

    @Test
    void shouldThrowExceptionWhenProductNotFound() {
        // Given
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> productVariantService.createVariant(productId, createRequest))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository).findById(productId);
        verify(variantRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenProductIsInactive() {
        // Given
        UUID productId = testProduct.getId();
        testProduct.setActive(false);
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

        // When/Then
        assertThatThrownBy(() -> productVariantService.createVariant(productId, createRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot create variant for inactive product");

        verify(productRepository).findById(productId);
        verify(variantRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenSkuAlreadyExists() {
        // Given
        UUID productId = testProduct.getId();
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(variantRepository.existsBySku(createRequest.getSku())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> productVariantService.createVariant(productId, createRequest))
                .isInstanceOf(DuplicateSkuException.class);

        verify(productRepository).findById(productId);
        verify(variantRepository).existsBySku(createRequest.getSku());
        verify(variantRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenGtinAlreadyExists() {
        // Given
        UUID productId = testProduct.getId();
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(variantRepository.existsBySku(createRequest.getSku())).thenReturn(false);
        when(variantRepository.existsByGtin(createRequest.getGtin())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> productVariantService.createVariant(productId, createRequest))
                .isInstanceOf(DuplicateGtinException.class);

        verify(productRepository).findById(productId);
        verify(variantRepository).existsByGtin(createRequest.getGtin());
        verify(variantRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenAttributeDefinitionInactive() {
        // Given
        UUID productId = testProduct.getId();
        testDefinition.setStatus(AttributeStatus.INACTIVE);

        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(variantRepository.existsBySku(createRequest.getSku())).thenReturn(false);
        when(variantRepository.existsByGtin(createRequest.getGtin())).thenReturn(false);
        when(definitionRepository.findById(testDefinition.getId())).thenReturn(Optional.of(testDefinition));

        // When/Then
        assertThatThrownBy(() -> productVariantService.createVariant(productId, createRequest))
                .isInstanceOf(InactiveAttributeException.class);

        verify(definitionRepository).findById(testDefinition.getId());
        verify(variantRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenAttributeValueInactive() {
        // Given
        UUID productId = testProduct.getId();
        testValue.setStatus(AttributeStatus.INACTIVE);

        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(variantRepository.existsBySku(createRequest.getSku())).thenReturn(false);
        when(variantRepository.existsByGtin(createRequest.getGtin())).thenReturn(false);
        when(definitionRepository.findById(testDefinition.getId())).thenReturn(Optional.of(testDefinition));
        when(valueRepository.findById(testValue.getId())).thenReturn(Optional.of(testValue));

        // When/Then
        assertThatThrownBy(() -> productVariantService.createVariant(productId, createRequest))
                .isInstanceOf(InactiveAttributeException.class);

        verify(valueRepository).findById(testValue.getId());
        verify(variantRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenDuplicateVariantCombination() {
        // Given
        UUID productId = testProduct.getId();
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(variantRepository.existsBySku(createRequest.getSku())).thenReturn(false);
        when(variantRepository.existsByGtin(createRequest.getGtin())).thenReturn(false);
        when(definitionRepository.findById(testDefinition.getId())).thenReturn(Optional.of(testDefinition));
        when(valueRepository.findById(testValue.getId())).thenReturn(Optional.of(testValue));
        when(definitionRepository.findAll()).thenReturn(List.of(testDefinition));
        when(hashCalculator.calculateHash(anyList())).thenReturn("test-hash");
        when(variantRepository.existsByProductIdAndAttributesHash(productId, "test-hash")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> productVariantService.createVariant(productId, createRequest))
                .isInstanceOf(DuplicateVariantCombinationException.class);

        verify(variantRepository).existsByProductIdAndAttributesHash(productId, "test-hash");
        verify(variantRepository, never()).save(any());
    }

    @Test
    void shouldGetVariantsByProductSuccessfully() {
        // Given
        UUID productId = testProduct.getId();
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProductVariant> variantPage = new PageImpl<>(List.of(testVariant));
        when(productRepository.existsById(productId)).thenReturn(true);
        when(variantRepository.findAllByProductId(productId, pageable)).thenReturn(variantPage);

        // When
        Page<ProductVariant> result = productVariantService.getVariantsByProduct(productId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(productRepository).existsById(productId);
        verify(variantRepository).findAllByProductId(productId, pageable);
    }

    @Test
    void shouldThrowExceptionWhenGettingVariantsByNonExistentProduct() {
        // Given
        UUID productId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.existsById(productId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> productVariantService.getVariantsByProduct(productId, pageable))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository).existsById(productId);
        verify(variantRepository, never()).findAllByProductId(any(), any());
    }

    @Test
    void shouldGetAllVariantsSuccessfully() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProductVariant> variantPage = new PageImpl<>(List.of(testVariant));
        when(variantRepository.findAll(pageable)).thenReturn(variantPage);

        // When
        Page<ProductVariant> result = productVariantService.getAllVariants(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(variantRepository).findAll(pageable);
    }

    @Test
    void shouldGetActiveVariantsSuccessfully() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProductVariant> variantPage = new PageImpl<>(List.of(testVariant));
        when(variantRepository.findByActiveTrue(pageable)).thenReturn(variantPage);

        // When
        Page<ProductVariant> result = productVariantService.getActiveVariants(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(variantRepository).findByActiveTrue(pageable);
    }

    @Test
    void shouldGetVariantByIdSuccessfully() {
        // Given
        UUID variantId = testVariant.getId();
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(testVariant));

        // When
        ProductVariant foundVariant = productVariantService.getVariantById(variantId);

        // Then
        assertThat(foundVariant).isNotNull();
        assertThat(foundVariant.getId()).isEqualTo(variantId);
        verify(variantRepository).findById(variantId);
    }

    @Test
    void shouldThrowExceptionWhenVariantNotFoundById() {
        // Given
        UUID variantId = UUID.randomUUID();
        when(variantRepository.findById(variantId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> productVariantService.getVariantById(variantId))
                .isInstanceOf(ProductVariantNotFoundException.class);

        verify(variantRepository).findById(variantId);
    }

    @Test
    void shouldGetVariantBySkuSuccessfully() {
        // Given
        String sku = testVariant.getSku();
        when(variantRepository.findBySku(sku)).thenReturn(Optional.of(testVariant));

        // When
        Optional<ProductVariant> foundVariant = productVariantService.getVariantBySku(sku);

        // Then
        assertThat(foundVariant).isPresent();
        assertThat(foundVariant.get().getSku()).isEqualTo(sku);
        verify(variantRepository).findBySku(sku);
    }

    @Test
    void shouldGetVariantByGtinSuccessfully() {
        // Given
        String gtin = testVariant.getGtin();
        when(variantRepository.findByGtin(gtin)).thenReturn(Optional.of(testVariant));

        // When
        Optional<ProductVariant> foundVariant = productVariantService.getVariantByGtin(gtin);

        // Then
        assertThat(foundVariant).isPresent();
        assertThat(foundVariant.get().getGtin()).isEqualTo(gtin);
        verify(variantRepository).findByGtin(gtin);
    }

    @Test
    void shouldUpdateVariantSuccessfully() {
        // Given
        UUID variantId = testVariant.getId();
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(testVariant));
        when(variantRepository.existsByGtinAndIdNot(updateRequest.getGtin(), variantId)).thenReturn(false);
        doNothing().when(mapper).updateEntity(updateRequest, testVariant);
        when(variantRepository.save(any(ProductVariant.class))).thenReturn(testVariant);

        // When
        ProductVariant updatedVariant = productVariantService.updateVariant(variantId, updateRequest);

        // Then
        assertThat(updatedVariant).isNotNull();
        verify(variantRepository).findById(variantId);
        verify(mapper).updateEntity(updateRequest, testVariant);
        verify(variantRepository).save(testVariant);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingWithDuplicateGtin() {
        // Given
        UUID variantId = testVariant.getId();
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(testVariant));
        when(variantRepository.existsByGtinAndIdNot(updateRequest.getGtin(), variantId)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> productVariantService.updateVariant(variantId, updateRequest))
                .isInstanceOf(DuplicateGtinException.class);

        verify(variantRepository).findById(variantId);
        verify(variantRepository, never()).save(any());
    }

    @Test
    void shouldDeleteVariantSuccessfully() {
        // Given
        UUID variantId = testVariant.getId();
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(testVariant));
        when(variantRepository.save(any(ProductVariant.class))).thenReturn(testVariant);

        // When
        productVariantService.deleteVariant(variantId);

        // Then
        assertThat(testVariant.getActive()).isFalse();
        verify(variantRepository).findById(variantId);
        verify(variantRepository).save(testVariant);
    }

    @Test
    void shouldActivateVariantSuccessfully() {
        // Given
        UUID variantId = testVariant.getId();
        testVariant.setActive(false);
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(testVariant));
        when(variantRepository.save(any(ProductVariant.class))).thenReturn(testVariant);

        // When
        productVariantService.activateVariant(variantId);

        // Then
        assertThat(testVariant.getActive()).isTrue();
        verify(variantRepository).findById(variantId);
        verify(variantRepository).save(testVariant);
    }
}
