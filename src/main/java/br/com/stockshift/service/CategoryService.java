package br.com.stockshift.service;

import br.com.stockshift.dto.product.CategoryRequest;
import br.com.stockshift.dto.product.CategoryResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Validate parent category if provided
        Category parentCategory = null;
        if (request.getParentCategoryId() != null) {
            parentCategory = categoryRepository.findByTenantIdAndId(tenantId, request.getParentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category", "id", request.getParentCategoryId()));
        }

        Category category = new Category();
        category.setTenantId(tenantId);
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setParentCategory(parentCategory);
        category.setAttributesSchema(request.getAttributesSchema());

        Category saved = categoryRepository.save(category);
        log.info("Created category {} for tenant {}", saved.getId(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return categoryRepository.findAllByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Category category = categoryRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        return mapToResponse(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findByParentId(UUID parentId) {
        return categoryRepository.findByParentCategoryId(parentId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        Category category = categoryRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // Validate parent category if provided
        if (request.getParentCategoryId() != null) {
            if (request.getParentCategoryId().equals(id)) {
                throw new BusinessException("Category cannot be its own parent");
            }
            Category parentCategory = categoryRepository.findByTenantIdAndId(tenantId, request.getParentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category", "id", request.getParentCategoryId()));
            category.setParentCategory(parentCategory);
        } else {
            category.setParentCategory(null);
        }

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setAttributesSchema(request.getAttributesSchema());

        Category updated = categoryRepository.save(category);
        log.info("Updated category {} for tenant {}", id, tenantId);

        return mapToResponse(updated);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Category category = categoryRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // Soft delete
        category.setDeletedAt(LocalDateTime.now());
        categoryRepository.save(category);

        log.info("Soft deleted category {} for tenant {}", id, tenantId);
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .parentCategoryId(category.getParentCategory() != null ? category.getParentCategory().getId() : null)
                .parentCategoryName(category.getParentCategory() != null ? category.getParentCategory().getName() : null)
                .attributesSchema(category.getAttributesSchema())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
