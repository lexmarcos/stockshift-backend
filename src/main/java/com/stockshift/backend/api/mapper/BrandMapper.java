package com.stockshift.backend.api.mapper;

import com.stockshift.backend.api.dto.brand.BrandResponse;
import com.stockshift.backend.domain.brand.Brand;
import org.springframework.stereotype.Component;

@Component
public class BrandMapper {

    public BrandResponse toResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .description(brand.getDescription())
                .active(brand.getActive())
                .createdAt(brand.getCreatedAt())
                .updatedAt(brand.getUpdatedAt())
                .build();
    }
}
