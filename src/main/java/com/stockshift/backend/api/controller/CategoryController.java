package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.category.CategoryResponse;
import com.stockshift.backend.api.dto.category.CreateCategoryRequest;
import com.stockshift.backend.api.dto.category.UpdateCategoryRequest;
import com.stockshift.backend.api.mapper.CategoryMapper;
import com.stockshift.backend.application.service.CategoryService;
import com.stockshift.backend.domain.category.Category;
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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        Category category = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryMapper.toResponse(category));
    }

    @GetMapping
    public ResponseEntity<Page<CategoryResponse>> getAllCategories(
            @RequestParam(value = "onlyActive", required = false, defaultValue = "false") Boolean onlyActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Category> categories = onlyActive 
            ? categoryService.getActiveCategories(pageable)
            : categoryService.getAllCategories(pageable);
        return ResponseEntity.ok(categories.map(categoryMapper::toResponse));
    }

    @GetMapping("/root")
    public ResponseEntity<Page<CategoryResponse>> getRootCategories(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Category> categories = categoryService.getRootCategories(pageable);
        return ResponseEntity.ok(categories.map(categoryMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable(value = "id") UUID id) {
        Category category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(categoryMapper.toResponse(category));
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<CategoryResponse> getCategoryByName(@PathVariable(value = "name") String name) {
        Category category = categoryService.getCategoryByName(name);
        return ResponseEntity.ok(categoryMapper.toResponse(category));
    }

    @GetMapping("/{id}/subcategories")
    public ResponseEntity<Page<CategoryResponse>> getSubcategories(
            @PathVariable(value = "id") UUID id,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<Category> subcategories = categoryService.getSubcategories(id, pageable);
        return ResponseEntity.ok(subcategories.map(categoryMapper::toResponse));
    }

    @GetMapping("/{id}/descendants")
    public ResponseEntity<List<CategoryResponse>> getDescendants(@PathVariable(value = "id") UUID id) {
        List<Category> descendants = categoryService.getDescendants(id);
        return ResponseEntity.ok(descendants.stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable(value = "id") UUID id,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        Category category = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(categoryMapper.toResponse(category));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable(value = "id") UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> activateCategory(@PathVariable(value = "id") UUID id) {
        categoryService.activateCategory(id);
        Category category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(categoryMapper.toResponse(category));
    }
}
