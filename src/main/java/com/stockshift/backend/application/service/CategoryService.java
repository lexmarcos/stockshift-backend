package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.category.CreateCategoryRequest;
import com.stockshift.backend.api.dto.category.UpdateCategoryRequest;
import com.stockshift.backend.domain.category.Category;
import com.stockshift.backend.domain.category.exception.CategoryAlreadyExistsException;
import com.stockshift.backend.domain.category.exception.CategoryNotFoundException;
import com.stockshift.backend.domain.category.exception.CircularCategoryReferenceException;
import com.stockshift.backend.infrastructure.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public Category createCategory(CreateCategoryRequest request) {
        // Check if category already exists
        if (categoryRepository.existsByNameAndActiveTrue(request.getName())) {
            throw new CategoryAlreadyExistsException(request.getName());
        }

        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());

        // Set parent if provided
        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new CategoryNotFoundException(request.getParentId()));
            
            if (!parent.getActive()) {
                throw new IllegalArgumentException("Parent category is not active");
            }
            
            category.setParent(parent);
        }

        // Update path and level
        category.updatePath();

        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public Page<Category> getAllCategories(Pageable pageable) {
        return categoryRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Category> getActiveCategories(Pageable pageable) {
        return categoryRepository.findAllByActiveTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Category> getRootCategories(Pageable pageable) {
        return categoryRepository.findRootCategories(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Category> getSubcategories(UUID parentId, Pageable pageable) {
        // Verify parent exists
        if (!categoryRepository.existsById(parentId)) {
            throw new CategoryNotFoundException(parentId);
        }
        return categoryRepository.findByParentId(parentId, pageable);
    }

    @Transactional(readOnly = true)
    public Category getCategoryById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Category getCategoryByName(String name) {
        return categoryRepository.findByNameAndActiveTrue(name)
                .orElseThrow(() -> new CategoryNotFoundException(name));
    }

    @Transactional(readOnly = true)
    public List<Category> getDescendants(UUID categoryId) {
        Category category = getCategoryById(categoryId);
        return categoryRepository.findDescendants(category.getPath());
    }

    @Transactional
    public Category updateCategory(UUID id, UpdateCategoryRequest request) {
        Category category = getCategoryById(id);

        // Check if new name conflicts with existing category
        if (request.getName() != null && !request.getName().equals(category.getName())) {
            if (categoryRepository.existsByNameAndActiveTrueAndIdNot(request.getName(), id)) {
                throw new CategoryAlreadyExistsException(request.getName());
            }
            category.setName(request.getName());
        }

        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }

        // Update parent if provided
        if (request.getParentId() != null) {
            if (!request.getParentId().equals(category.getParent() != null ? category.getParent().getId() : null)) {
                Category newParent = categoryRepository.findById(request.getParentId())
                        .orElseThrow(() -> new CategoryNotFoundException(request.getParentId()));

                if (!newParent.getActive()) {
                    throw new IllegalArgumentException("Parent category is not active");
                }

                // Check for circular reference
                if (category.hasCircularReference(newParent)) {
                    throw new CircularCategoryReferenceException();
                }

                category.setParent(newParent);
                category.updatePath();
                
                // Update paths of all descendants
                updateDescendantPaths(category);
            }
        }

        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = getCategoryById(id);
        
        // Soft delete
        category.setActive(false);
        
        // Also deactivate all descendants
        List<Category> descendants = categoryRepository.findDescendants(category.getPath());
        descendants.forEach(descendant -> descendant.setActive(false));
        
        categoryRepository.save(category);
        categoryRepository.saveAll(descendants);
    }

    @Transactional
    public void activateCategory(UUID id) {
        Category category = getCategoryById(id);
        
        // Check if parent is active (if has parent)
        if (category.getParent() != null && !category.getParent().getActive()) {
            throw new IllegalArgumentException("Cannot activate category: parent category is not active");
        }
        
        category.setActive(true);
        categoryRepository.save(category);
    }

    private void updateDescendantPaths(Category category) {
        List<Category> descendants = categoryRepository.findDescendants(category.getPath());
        for (Category descendant : descendants) {
            descendant.updatePath();
        }
        categoryRepository.saveAll(descendants);
    }
}
