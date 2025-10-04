package com.stockshift.backend.api.mapper;

import com.stockshift.backend.api.dto.variant.CreateProductVariantRequest;
import com.stockshift.backend.api.dto.variant.ProductVariantResponse;
import com.stockshift.backend.api.dto.variant.UpdateProductVariantRequest;
import com.stockshift.backend.domain.product.ProductVariant;
import com.stockshift.backend.domain.product.ProductVariantAttribute;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ProductVariantMapper {

    public ProductVariant toEntity(CreateProductVariantRequest request) {
        ProductVariant variant = new ProductVariant();
        variant.setSku(request.getSku());
        variant.setGtin(request.getGtin());
        variant.setPrice(request.getPrice());
        variant.setWeight(request.getWeight());
        variant.setLength(request.getLength());
        variant.setWidth(request.getWidth());
        variant.setHeight(request.getHeight());
        variant.setActive(true);
        return variant;
    }

    public void updateEntity(UpdateProductVariantRequest request, ProductVariant variant) {
        if (request.getGtin() != null) {
            variant.setGtin(request.getGtin());
        }
        if (request.getPrice() != null) {
            variant.setPrice(request.getPrice());
        }
        if (request.getWeight() != null) {
            variant.setWeight(request.getWeight());
        }
        if (request.getLength() != null) {
            variant.setLength(request.getLength());
        }
        if (request.getWidth() != null) {
            variant.setWidth(request.getWidth());
        }
        if (request.getHeight() != null) {
            variant.setHeight(request.getHeight());
        }
    }

    public ProductVariantResponse toResponse(ProductVariant variant) {
        ProductVariantResponse response = new ProductVariantResponse();
        response.setId(variant.getId());
        response.setProductId(variant.getProduct().getId());
        response.setProductName(variant.getProduct().getName());
        response.setSku(variant.getSku());
        response.setGtin(variant.getGtin());
        response.setAttributesHash(variant.getAttributesHash());
        response.setPrice(variant.getPrice());
        response.setEffectivePrice(variant.getEffectivePrice());
        response.setWeight(variant.getWeight());
        response.setLength(variant.getLength());
        response.setWidth(variant.getWidth());
        response.setHeight(variant.getHeight());
        response.setActive(variant.getActive());
        response.setCreatedAt(variant.getCreatedAt());
        response.setUpdatedAt(variant.getUpdatedAt());

        if (variant.getAttributes() != null && !variant.getAttributes().isEmpty()) {
            response.setAttributes(
                variant.getAttributes().stream()
                    .map(this::toAttributeInfo)
                    .collect(Collectors.toList())
            );
        }

        return response;
    }

    private ProductVariantResponse.VariantAttributeInfo toAttributeInfo(ProductVariantAttribute attr) {
        ProductVariantResponse.VariantAttributeInfo info = new ProductVariantResponse.VariantAttributeInfo();
        info.setValueId(attr.getValue().getId());
        info.setValue(attr.getValue().getValue());
        info.setValueCode(attr.getValue().getCode());
        info.setDefinitionId(attr.getDefinition().getId());
        info.setDefinitionName(attr.getDefinition().getName());
        info.setDefinitionCode(attr.getDefinition().getCode());
        info.setDefinitionType(attr.getDefinition().getType());
        info.setIsVariantDefining(attr.getDefinition().getIsVariantDefining());
        return info;
    }
}
