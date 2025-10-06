package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.category.CreateCategoryRequest;
import com.stockshift.backend.api.dto.category.UpdateCategoryRequest;
import com.stockshift.backend.domain.category.Category;
import com.stockshift.backend.domain.category.exception.CategoryAlreadyExistsException;
import com.stockshift.backend.domain.category.exception.CategoryNotFoundException;
import com.stockshift.backend.domain.category.exception.CircularCategoryReferenceException;
import com.stockshift.backend.infrastructure.repository.CategoryRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category testCategory;
    private Category parentCategory;
    private CreateCategoryRequest createCategoryRequest;
    private UpdateCategoryRequest updateCategoryRequest;

    @BeforeEach
    void setUp() {
        testCategory = new Category();
        testCategory.setId(UUID.randomUUID());
        testCategory.setName("Test Category");
        testCategory.setDescription("Test Description");
        testCategory.setActive(true);
        testCategory.setLevel(0);
        testCategory.setPath("/" + testCategory.getId());

        parentCategory = new Category();
        parentCategory.setId(UUID.randomUUID());
        parentCategory.setName("Parent Category");
        parentCategory.setDescription("Parent Description");
        parentCategory.setActive(true);
        parentCategory.setLevel(0);
        parentCategory.setPath("/" + parentCategory.getId());

        createCategoryRequest = new CreateCategoryRequest(
                "New Category",
                "New Description",
                null
        );

        updateCategoryRequest = new UpdateCategoryRequest(
                "Updated Category",
                "Updated Description",
                null
        );
    }

    @Test
    void shouldCreateRootCategorySuccessfully() {
        // Given
        when(categoryRepository.existsByNameAndActiveTrue(createCategoryRequest.getName())).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);

        // When
        Category createdCategory = categoryService.createCategory(createCategoryRequest);

        // Then
        assertThat(createdCategory).isNotNull();
        verify(categoryRepository).existsByNameAndActiveTrue(createCategoryRequest.getName());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void shouldCreateSubcategorySuccessfully() {
        // Given
        createCategoryRequest = new CreateCategoryRequest(
                "Subcategory",
                "Subcategory Description",
                parentCategory.getId()
        );

        when(categoryRepository.existsByNameAndActiveTrue(createCategoryRequest.getName())).thenReturn(false);
        when(categoryRepository.findById(parentCategory.getId())).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);

        // When
        Category createdCategory = categoryService.createCategory(createCategoryRequest);

        // Then
        assertThat(createdCategory).isNotNull();
        verify(categoryRepository).existsByNameAndActiveTrue(createCategoryRequest.getName());
        verify(categoryRepository).findById(parentCategory.getId());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void shouldThrowExceptionWhenCategoryNameAlreadyExists() {
        // Given
        when(categoryRepository.existsByNameAndActiveTrue(createCategoryRequest.getName())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> categoryService.createCategory(createCategoryRequest))
                .isInstanceOf(CategoryAlreadyExistsException.class);

        verify(categoryRepository).existsByNameAndActiveTrue(createCategoryRequest.getName());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void shouldThrowExceptionWhenParentCategoryNotFound() {
        // Given
        createCategoryRequest = new CreateCategoryRequest(
                "Subcategory",
                "Description",
                UUID.randomUUID()
        );

        when(categoryRepository.existsByNameAndActiveTrue(createCategoryRequest.getName())).thenReturn(false);
        when(categoryRepository.findById(createCategoryRequest.getParentId())).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> categoryService.createCategory(createCategoryRequest))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(categoryRepository).existsByNameAndActiveTrue(createCategoryRequest.getName());
        verify(categoryRepository).findById(createCategoryRequest.getParentId());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void shouldThrowExceptionWhenParentCategoryIsInactive() {
        // Given
        parentCategory.setActive(false);
        createCategoryRequest = new CreateCategoryRequest(
                "Subcategory",
                "Description",
                parentCategory.getId()
        );

        when(categoryRepository.existsByNameAndActiveTrue(createCategoryRequest.getName())).thenReturn(false);
        when(categoryRepository.findById(parentCategory.getId())).thenReturn(Optional.of(parentCategory));

        // When/Then
        assertThatThrownBy(() -> categoryService.createCategory(createCategoryRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parent category is not active");

        verify(categoryRepository).existsByNameAndActiveTrue(createCategoryRequest.getName());
        verify(categoryRepository).findById(parentCategory.getId());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void shouldGetAllCategoriesSuccessfully() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Category> categoryPage = new PageImpl<>(List.of(testCategory));
        when(categoryRepository.findAll(pageable)).thenReturn(categoryPage);

        // When
        Page<Category> result = categoryService.getAllCategories(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isEqualTo(testCategory);
        verify(categoryRepository).findAll(pageable);
    }

    @Test
    void shouldGetActiveCategoriesSuccessfully() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Category> categoryPage = new PageImpl<>(List.of(testCategory));
        when(categoryRepository.findAllByActiveTrue(pageable)).thenReturn(categoryPage);

        // When
        Page<Category> result = categoryService.getActiveCategories(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(categoryRepository).findAllByActiveTrue(pageable);
    }

    @Test
    void shouldGetRootCategoriesSuccessfully() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Category> categoryPage = new PageImpl<>(List.of(testCategory));
        when(categoryRepository.findRootCategories(pageable)).thenReturn(categoryPage);

        // When
        Page<Category> result = categoryService.getRootCategories(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(categoryRepository).findRootCategories(pageable);
    }

    @Test
    void shouldGetSubcategoriesSuccessfully() {
        // Given
        UUID parentId = parentCategory.getId();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Category> categoryPage = new PageImpl<>(List.of(testCategory));
        when(categoryRepository.existsById(parentId)).thenReturn(true);
        when(categoryRepository.findByParentId(parentId, pageable)).thenReturn(categoryPage);

        // When
        Page<Category> result = categoryService.getSubcategories(parentId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(categoryRepository).existsById(parentId);
        verify(categoryRepository).findByParentId(parentId, pageable);
    }

    @Test
    void shouldThrowExceptionWhenGettingSubcategoriesOfNonExistentParent() {
        // Given
        UUID parentId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        when(categoryRepository.existsById(parentId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> categoryService.getSubcategories(parentId, pageable))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(categoryRepository).existsById(parentId);
        verify(categoryRepository, never()).findByParentId(any(), any());
    }

    @Test
    void shouldGetCategoryByIdSuccessfully() {
        // Given
        UUID categoryId = testCategory.getId();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));

        // When
        Category foundCategory = categoryService.getCategoryById(categoryId);

        // Then
        assertThat(foundCategory).isNotNull();
        assertThat(foundCategory.getId()).isEqualTo(categoryId);
        verify(categoryRepository).findById(categoryId);
    }

    @Test
    void shouldThrowExceptionWhenCategoryNotFoundById() {
        // Given
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> categoryService.getCategoryById(categoryId))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(categoryRepository).findById(categoryId);
    }

    @Test
    void shouldGetCategoryByNameSuccessfully() {
        // Given
        String categoryName = testCategory.getName();
        when(categoryRepository.findByNameAndActiveTrue(categoryName)).thenReturn(Optional.of(testCategory));

        // When
        Category foundCategory = categoryService.getCategoryByName(categoryName);

        // Then
        assertThat(foundCategory).isNotNull();
        assertThat(foundCategory.getName()).isEqualTo(categoryName);
        verify(categoryRepository).findByNameAndActiveTrue(categoryName);
    }

    @Test
    void shouldThrowExceptionWhenCategoryNotFoundByName() {
        // Given
        String categoryName = "Nonexistent";
        when(categoryRepository.findByNameAndActiveTrue(categoryName)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> categoryService.getCategoryByName(categoryName))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(categoryRepository).findByNameAndActiveTrue(categoryName);
    }

    @Test
    void shouldGetDescendantsSuccessfully() {
        // Given
        UUID categoryId = testCategory.getId();
        List<Category> descendants = List.of(new Category(), new Category());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.findDescendants(testCategory.getPath())).thenReturn(descendants);

        // When
        List<Category> result = categoryService.getDescendants(categoryId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        verify(categoryRepository).findById(categoryId);
        verify(categoryRepository).findDescendants(testCategory.getPath());
    }

    @Test
    void shouldUpdateCategorySuccessfully() {
        // Given
        UUID categoryId = testCategory.getId();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByNameAndActiveTrueAndIdNot(updateCategoryRequest.getName(), categoryId))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);

        // When
        Category updatedCategory = categoryService.updateCategory(categoryId, updateCategoryRequest);

        // Then
        assertThat(updatedCategory).isNotNull();
        verify(categoryRepository).findById(categoryId);
        verify(categoryRepository).save(testCategory);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingWithExistingName() {
        // Given
        UUID categoryId = testCategory.getId();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByNameAndActiveTrueAndIdNot(updateCategoryRequest.getName(), categoryId))
                .thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, updateCategoryRequest))
                .isInstanceOf(CategoryAlreadyExistsException.class);

        verify(categoryRepository).findById(categoryId);
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void shouldUpdateCategoryWithNewParent() {
        // Given
        UUID categoryId = testCategory.getId();
        updateCategoryRequest.setParentId(parentCategory.getId());

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.findById(parentCategory.getId())).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.existsByNameAndActiveTrueAndIdNot(updateCategoryRequest.getName(), categoryId))
                .thenReturn(false);
        when(categoryRepository.findDescendants(anyString())).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);
        when(categoryRepository.saveAll(anyList())).thenReturn(List.of());

        // When
        Category updatedCategory = categoryService.updateCategory(categoryId, updateCategoryRequest);

        // Then
        assertThat(updatedCategory).isNotNull();
        verify(categoryRepository).findById(categoryId);
        verify(categoryRepository).findById(parentCategory.getId());
        verify(categoryRepository).save(testCategory);
    }

    @Test
    void shouldDeleteCategoryAndDescendantsSuccessfully() {
        // Given
        UUID categoryId = testCategory.getId();
        Category descendant1 = new Category();
        descendant1.setActive(true);
        Category descendant2 = new Category();
        descendant2.setActive(true);
        List<Category> descendants = List.of(descendant1, descendant2);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.findDescendants(testCategory.getPath())).thenReturn(descendants);
        when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);
        when(categoryRepository.saveAll(descendants)).thenReturn(descendants);

        // When
        categoryService.deleteCategory(categoryId);

        // Then
        assertThat(testCategory.getActive()).isFalse();
        assertThat(descendant1.getActive()).isFalse();
        assertThat(descendant2.getActive()).isFalse();
        verify(categoryRepository).findById(categoryId);
        verify(categoryRepository).findDescendants(testCategory.getPath());
        verify(categoryRepository).save(testCategory);
        verify(categoryRepository).saveAll(descendants);
    }

    @Test
    void shouldActivateCategorySuccessfully() {
        // Given
        UUID categoryId = testCategory.getId();
        testCategory.setActive(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);

        // When
        categoryService.activateCategory(categoryId);

        // Then
        assertThat(testCategory.getActive()).isTrue();
        verify(categoryRepository).findById(categoryId);
        verify(categoryRepository).save(testCategory);
    }

    @Test
    void shouldThrowExceptionWhenActivatingCategoryWithInactiveParent() {
        // Given
        UUID categoryId = testCategory.getId();
        parentCategory.setActive(false);
        testCategory.setParent(parentCategory);
        testCategory.setActive(false);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));

        // When/Then
        assertThatThrownBy(() -> categoryService.activateCategory(categoryId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot activate category: parent category is not active");

        verify(categoryRepository).findById(categoryId);
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void shouldThrowExceptionWhenDeletingNonExistentCategory() {
        // Given
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> categoryService.deleteCategory(categoryId))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(categoryRepository).findById(categoryId);
        verify(categoryRepository, never()).save(any(Category.class));
    }
}
