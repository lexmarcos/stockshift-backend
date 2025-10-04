package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.variant.CreateProductVariantRequest;
import com.stockshift.backend.api.dto.variant.UpdateProductVariantRequest;
import com.stockshift.backend.api.mapper.ProductVariantMapper;
import com.stockshift.backend.application.exception.*;
import com.stockshift.backend.application.util.AttributeHashCalculator;
import com.stockshift.backend.domain.attribute.AttributeDefinition;
import com.stockshift.backend.domain.attribute.AttributeStatus;
import com.stockshift.backend.domain.attribute.AttributeValue;
import com.stockshift.backend.domain.attribute.exception.AttributeDefinitionNotFoundException;
import com.stockshift.backend.domain.attribute.exception.AttributeValueNotFoundException;
import com.stockshift.backend.domain.product.Product;
import com.stockshift.backend.domain.product.ProductVariant;
import com.stockshift.backend.domain.product.ProductVariantAttribute;
import com.stockshift.backend.domain.product.exception.ProductNotFoundException;
import com.stockshift.backend.domain.product.exception.ProductVariantNotFoundException;
import com.stockshift.backend.infrastructure.repository.AttributeDefinitionRepository;
import com.stockshift.backend.infrastructure.repository.AttributeValueRepository;
import com.stockshift.backend.infrastructure.repository.ProductRepository;
import com.stockshift.backend.infrastructure.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final AttributeDefinitionRepository definitionRepository;
    private final AttributeValueRepository valueRepository;
    private final ProductVariantMapper mapper;
    private final AttributeHashCalculator hashCalculator;

    @Transactional
    public ProductVariant createVariant(UUID productId, CreateProductVariantRequest request) {
        // 1. Verify product exists and is active
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
        
        if (!product.getActive()) {
            throw new IllegalStateException("Cannot create variant for inactive product");
        }

        // 2. Verify SKU is unique
        if (variantRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }

        // 3. Verify GTIN is unique if provided
        if (request.getGtin() != null && !request.getGtin().isBlank()) {
            if (variantRepository.existsByGtin(request.getGtin())) {
                throw new DuplicateGtinException(request.getGtin());
            }
        }

        // 4. Validate attributes
        Map<UUID, AttributeDefinition> definitions = new HashMap<>();
        Map<UUID, AttributeValue> values = new HashMap<>();
        Set<UUID> seenDefinitions = new HashSet<>();

        for (CreateProductVariantRequest.VariantAttributePair pair : request.getAttributes()) {
            UUID defId = pair.getDefinitionId();
            UUID valId = pair.getValueId();

            // Check for duplicate definitions
            if (seenDefinitions.contains(defId)) {
                throw new IllegalArgumentException("Duplicate definition " + defId + " in attribute list");
            }
            seenDefinitions.add(defId);

            // Load definition
            AttributeDefinition definition = definitionRepository.findById(defId)
                .orElseThrow(() -> new AttributeDefinitionNotFoundException(defId));
            
            if (definition.getStatus() == AttributeStatus.INACTIVE) {
                throw new InactiveAttributeException("definition", definition.getCode());
            }

            // Load value
            AttributeValue value = valueRepository.findById(valId)
                .orElseThrow(() -> new AttributeValueNotFoundException(valId));
            
            if (value.getStatus() == AttributeStatus.INACTIVE) {
                throw new InactiveAttributeException("value", value.getCode());
            }

            // Verify value belongs to definition
            if (!value.getDefinition().getId().equals(defId)) {
                throw new InvalidAttributePairException(defId, valId);
            }

            // Verify definition applies to product's category
            if (product.getCategory() != null) {
                if (!definition.isApplicableToCategory(product.getCategory().getId())) {
                    throw new AttributeNotApplicableException(definition.getCode(), product.getCategory().getId());
                }
            }

            definitions.put(defId, definition);
            values.put(defId, value);
        }

        // 5. Validate required variant-defining attributes
        List<AttributeDefinition> applicableDefinitions = getApplicableDefinitions(product);
        for (AttributeDefinition def : applicableDefinitions) {
            if (def.getIsRequired() && def.getIsVariantDefining()) {
                if (!definitions.containsKey(def.getId())) {
                    throw new MissingRequiredAttributeException(def.getCode());
                }
            }
        }

        // 6. Calculate attributes hash
        String attributesHash = hashCalculator.calculateHash(request.getAttributes());

        // 7. Check for duplicate combination
        if (variantRepository.existsByProductIdAndAttributesHash(productId, attributesHash)) {
            throw new DuplicateVariantCombinationException("A variant with this attribute combination already exists");
        }

        // 8. Create variant
        ProductVariant variant = mapper.toEntity(request);
        variant.setProduct(product);
        variant.setAttributesHash(attributesHash);

        // 9. Save variant first to get ID
        variant = variantRepository.save(variant);

        // 10. Create ProductVariantAttribute entries
        List<ProductVariantAttribute> attributeEntries = new ArrayList<>();
        for (Map.Entry<UUID, AttributeValue> entry : values.entrySet()) {
            ProductVariantAttribute attr = new ProductVariantAttribute();
            attr.setVariant(variant);
            attr.setDefinition(definitions.get(entry.getKey()));
            attr.setValue(entry.getValue());
            attributeEntries.add(attr);
        }
        variant.getAttributes().addAll(attributeEntries);

        return variantRepository.save(variant);
    }

    private List<AttributeDefinition> getApplicableDefinitions(Product product) {
        List<AttributeDefinition> allDefinitions = definitionRepository.findAll();
        return allDefinitions.stream()
            .filter(def -> def.getStatus() == AttributeStatus.ACTIVE)
            .filter(def -> product.getCategory() == null || def.isApplicableToCategory(product.getCategory().getId()))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ProductVariant> getVariantsByProduct(UUID productId, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        return variantRepository.findAllByProductId(productId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductVariant> getAllVariants(Pageable pageable) {
        return variantRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductVariant> getActiveVariants(Pageable pageable) {
        return variantRepository.findByActiveTrue(pageable);
    }

    @Transactional(readOnly = true)
    public ProductVariant getVariantById(UUID id) {
        return variantRepository.findById(id)
            .orElseThrow(() -> new ProductVariantNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Optional<ProductVariant> getVariantBySku(String sku) {
        return variantRepository.findBySku(sku);
    }

    @Transactional(readOnly = true)
    public Optional<ProductVariant> getVariantByGtin(String gtin) {
        return variantRepository.findByGtin(gtin);
    }

    @Transactional
    public ProductVariant updateVariant(UUID id, UpdateProductVariantRequest request) {
        ProductVariant variant = getVariantById(id);
        
        // Check GTIN uniqueness if being updated
        if (request.getGtin() != null && !request.getGtin().equals(variant.getGtin())) {
            if (variantRepository.existsByGtinAndIdNot(request.getGtin(), id)) {
                throw new DuplicateGtinException(request.getGtin());
            }
        }

        mapper.updateEntity(request, variant);
        return variantRepository.save(variant);
    }

    @Transactional
    public void deleteVariant(UUID id) {
        ProductVariant variant = getVariantById(id);
        variant.setActive(false);
        variantRepository.save(variant);
    }

    @Transactional
    public void activateVariant(UUID id) {
        ProductVariant variant = getVariantById(id);
        variant.setActive(true);
        variantRepository.save(variant);
    }
}
