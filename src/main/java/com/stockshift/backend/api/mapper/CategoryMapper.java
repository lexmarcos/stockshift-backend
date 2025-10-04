package com.stockshift.backend.api.mapper;

import com.stockshift.backend.api.dto.category.CategoryResponse;
import com.stockshift.backend.domain.category.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setDescription(category.getDescription());
        response.setPath(category.getPath());
        response.setLevel(category.getLevel());
        response.setActive(category.getActive());
        response.setCreatedAt(category.getCreatedAt());
        response.setUpdatedAt(category.getUpdatedAt());
        
        if (category.getParent() != null) {
            response.setParentId(category.getParent().getId());
            response.setParentName(category.getParent().getName());
        }
        
        return response;
    }
}
