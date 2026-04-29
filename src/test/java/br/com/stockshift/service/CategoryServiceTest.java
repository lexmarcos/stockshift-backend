package br.com.stockshift.service;

import br.com.stockshift.dto.product.CategoryRequest;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Category;
import br.com.stockshift.repository.CategoryRepository;
import br.com.stockshift.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        service = new CategoryService(categoryRepository);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            if (category.getId() == null) {
                category.setId(UUID.randomUUID());
            }
            return category;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createFindUpdateAndDeleteShouldMapCategoryHierarchy() {
        Category parent = category("Parent", null);
        Category child = category("Child", parent);
        CategoryRequest create = CategoryRequest.builder()
                .name("<b>Child</b>")
                .description("Description")
                .parentCategoryId(parent.getId())
                .attributesSchema(Map.of("color", "string"))
                .build();
        when(categoryRepository.findByTenantIdAndId(tenantId, parent.getId())).thenReturn(Optional.of(parent));
        when(categoryRepository.findAllByTenantId(tenantId)).thenReturn(List.of(parent, child));
        when(categoryRepository.findByParentCategoryId(parent.getId())).thenReturn(List.of(child));
        when(categoryRepository.findByTenantIdAndId(tenantId, child.getId())).thenReturn(Optional.of(child));

        assertThat(service.create(create).getParentCategoryName()).isEqualTo("Parent");
        assertThat(service.findAll()).hasSize(2);
        assertThat(service.findByParentId(parent.getId())).extracting("name").containsExactly("Child");
        assertThat(service.findById(child.getId()).getParentCategoryId()).isEqualTo(parent.getId());

        CategoryRequest update = CategoryRequest.builder()
                .name("Updated")
                .description("Updated description")
                .parentCategoryId(null)
                .attributesSchema(Map.of("size", "number"))
                .build();
        assertThat(service.update(child.getId(), update).getParentCategoryId()).isNull();

        service.delete(child.getId());
        assertThat(child.getDeletedAt()).isNotNull();
    }

    @Test
    void updateShouldRejectSelfParentAndMissingCategories() {
        Category category = category("Category", null);
        when(categoryRepository.findByTenantIdAndId(tenantId, category.getId())).thenReturn(Optional.of(category));

        CategoryRequest selfParent = CategoryRequest.builder()
                .name("Category")
                .parentCategoryId(category.getId())
                .build();
        assertThatThrownBy(() -> service.update(category.getId(), selfParent))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("own parent");

        UUID missingParentId = UUID.randomUUID();
        CategoryRequest missingParent = CategoryRequest.builder()
                .name("Category")
                .parentCategoryId(missingParentId)
                .build();
        when(categoryRepository.findByTenantIdAndId(tenantId, missingParentId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(category.getId(), missingParent))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Category category(String name, Category parent) {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setTenantId(tenantId);
        category.setName(name);
        category.setDescription(name + " description");
        category.setParentCategory(parent);
        category.setAttributesSchema(Map.of("field", "text"));
        return category;
    }
}
