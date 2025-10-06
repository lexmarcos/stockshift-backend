package com.stockshift.backend.api.controller;

import com.stockshift.backend.api.dto.category.CategoryResponse;
import com.stockshift.backend.api.dto.category.CreateCategoryRequest;
import com.stockshift.backend.api.dto.category.UpdateCategoryRequest;
import com.stockshift.backend.api.mapper.CategoryMapper;
import com.stockshift.backend.application.service.CategoryService;
import com.stockshift.backend.domain.category.Category;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryController categoryController;

    private Category category;
    private CategoryResponse categoryResponse;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Electronics");

        categoryResponse = new CategoryResponse();
        categoryResponse.setId(category.getId());
        categoryResponse.setName(category.getName());
    }

    @Test
    void createCategoryShouldReturnCreatedResponse() {
        CreateCategoryRequest request = new CreateCategoryRequest("Electronics", "desc", null);
        when(categoryService.createCategory(request)).thenReturn(category);
        when(categoryMapper.toResponse(category)).thenReturn(categoryResponse);

        ResponseEntity<CategoryResponse> response = categoryController.createCategory(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(categoryResponse);
    }

    @Test
    void getAllCategoriesShouldUseOnlyActiveWhenTrue() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Category> page = new PageImpl<>(List.of(category));
        when(categoryService.getActiveCategories(pageable)).thenReturn(page);
        when(categoryMapper.toResponse(category)).thenReturn(categoryResponse);

        ResponseEntity<Page<CategoryResponse>> response = categoryController.getAllCategories(true, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(categoryResponse);
        verify(categoryService).getActiveCategories(pageable);
    }

    @Test
    void getDescendantsShouldMapList() {
        UUID id = UUID.randomUUID();
        when(categoryService.getDescendants(id)).thenReturn(List.of(category));
        when(categoryMapper.toResponse(category)).thenReturn(categoryResponse);

        ResponseEntity<List<CategoryResponse>> response = categoryController.getDescendants(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(categoryResponse);
    }

    @Test
    void activateCategoryShouldReturnMappedResponse() {
        UUID id = UUID.randomUUID();
        when(categoryService.getCategoryById(id)).thenReturn(category);
        when(categoryMapper.toResponse(category)).thenReturn(categoryResponse);

        ResponseEntity<CategoryResponse> response = categoryController.activateCategory(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(categoryResponse);
        verify(categoryService).activateCategory(id);
    }
}
