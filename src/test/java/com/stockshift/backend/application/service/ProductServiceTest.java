package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.product.CreateProductRequest;
import com.stockshift.backend.api.dto.product.UpdateProductRequest;
import com.stockshift.backend.domain.brand.Brand;
import com.stockshift.backend.domain.brand.exception.BrandNotFoundException;
import com.stockshift.backend.domain.category.Category;
import com.stockshift.backend.domain.category.exception.CategoryNotFoundException;
import com.stockshift.backend.domain.product.Product;
import com.stockshift.backend.domain.product.exception.ProductAlreadyExistsException;
import com.stockshift.backend.domain.product.exception.ProductNotFoundException;
import com.stockshift.backend.infrastructure.repository.BrandRepository;
import com.stockshift.backend.infrastructure.repository.CategoryRepository;
import com.stockshift.backend.infrastructure.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;
    private Brand brand;
    private Category category;

    @BeforeEach
    void setUp() {
        brand = new Brand();
        brand.setId(UUID.randomUUID());
        brand.setName("Brand");
        brand.setActive(true);

        category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Category");
        category.setActive(true);

        product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Product");
        product.setDescription("Description");
        product.setBasePrice(100L);
        product.setExpiryDate(LocalDate.now().plusDays(10));
        product.setBrand(brand);
        product.setCategory(category);
        product.setActive(true);
    }

    @Test
    void shouldCreateProductWithoutAssociations() {
        CreateProductRequest request = new CreateProductRequest("New Product", "Desc", null, null, 200L, null);

        when(productRepository.existsByNameAndActiveTrue(request.getName())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(product);

        Product created = productService.createProduct(request);

        assertThat(created).isEqualTo(product);
        verify(productRepository).existsByNameAndActiveTrue(request.getName());
        verify(productRepository).save(any(Product.class));
        verifyNoInteractions(brandRepository, categoryRepository);
    }

    @Test
    void shouldCreateProductWithBrandAndCategory() {
        UUID brandId = brand.getId();
        UUID categoryId = category.getId();
        CreateProductRequest request = new CreateProductRequest("New Product", "Desc", brandId, categoryId, 200L, null);

        when(productRepository.existsByNameAndActiveTrue(request.getName())).thenReturn(false);
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.createProduct(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getBrand()).isEqualTo(brand);
        assertThat(saved.getCategory()).isEqualTo(category);
    }

    @Test
    void shouldThrowWhenCreatingProductWithExistingName() {
        CreateProductRequest request = new CreateProductRequest("Product", null, null, null, 100L, null);

        when(productRepository.existsByNameAndActiveTrue(request.getName())).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(ProductAlreadyExistsException.class)
                .hasMessageContaining("Product already exists with name: Product");

        verify(productRepository).existsByNameAndActiveTrue(request.getName());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void shouldThrowWhenBrandNotFoundOnCreate() {
        UUID brandId = UUID.randomUUID();
        CreateProductRequest request = new CreateProductRequest("Product", null, brandId, null, 100L, null);

        when(productRepository.existsByNameAndActiveTrue(request.getName())).thenReturn(false);
        when(brandRepository.findById(brandId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(BrandNotFoundException.class);

        verify(brandRepository).findById(brandId);
    }

    @Test
    void shouldThrowWhenBrandInactiveOnCreate() {
        UUID brandId = UUID.randomUUID();
        CreateProductRequest request = new CreateProductRequest("Product", null, brandId, null, 100L, null);
        Brand inactiveBrand = new Brand();
        inactiveBrand.setId(brandId);
        inactiveBrand.setActive(false);

        when(productRepository.existsByNameAndActiveTrue(request.getName())).thenReturn(false);
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(inactiveBrand));

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Brand is not active");
    }

    @Test
    void shouldThrowWhenCategoryNotFoundOnCreate() {
        UUID brandId = brand.getId();
        UUID categoryId = UUID.randomUUID();
        CreateProductRequest request = new CreateProductRequest("Product", null, brandId, categoryId, 100L, null);

        when(productRepository.existsByNameAndActiveTrue(request.getName())).thenReturn(false);
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void shouldThrowWhenCategoryInactiveOnCreate() {
        UUID brandId = brand.getId();
        UUID categoryId = UUID.randomUUID();
        CreateProductRequest request = new CreateProductRequest("Product", null, brandId, categoryId, 100L, null);
        Category inactiveCategory = new Category();
        inactiveCategory.setId(categoryId);
        inactiveCategory.setActive(false);

        when(productRepository.existsByNameAndActiveTrue(request.getName())).thenReturn(false);
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(inactiveCategory));

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category is not active");
    }

    @Test
    void shouldReturnAllProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findAll(pageable)).thenReturn(page);

        Page<Product> result = productService.getAllProducts(pageable);

        assertThat(result.getContent()).containsExactly(product);
        verify(productRepository).findAll(pageable);
    }

    @Test
    void shouldReturnActiveProducts() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findAllByActiveTrue(pageable)).thenReturn(page);

        Page<Product> result = productService.getActiveProducts(pageable);

        assertThat(result.getContent()).containsExactly(product);
        verify(productRepository).findAllByActiveTrue(pageable);
    }

    @Test
    void shouldGetProductsByBrand() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<Product> page = new PageImpl<>(List.of(product));
        UUID brandId = brand.getId();

        when(brandRepository.existsById(brandId)).thenReturn(true);
        when(productRepository.findByBrandId(brandId, pageable)).thenReturn(page);

        Page<Product> result = productService.getProductsByBrand(brandId, pageable);

        assertThat(result.getContent()).containsExactly(product);
        verify(productRepository).findByBrandId(brandId, pageable);
    }

    @Test
    void shouldThrowWhenBrandMissingOnGetProductsByBrand() {
        UUID brandId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 5);

        when(brandRepository.existsById(brandId)).thenReturn(false);

        assertThatThrownBy(() -> productService.getProductsByBrand(brandId, pageable))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void shouldGetProductsByCategory() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<Product> page = new PageImpl<>(List.of(product));
        UUID categoryId = category.getId();

        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(productRepository.findByCategoryId(categoryId, pageable)).thenReturn(page);

        Page<Product> result = productService.getProductsByCategory(categoryId, pageable);

        assertThat(result.getContent()).containsExactly(product);
        verify(productRepository).findByCategoryId(categoryId, pageable);
    }

    @Test
    void shouldThrowWhenCategoryMissingOnGetProductsByCategory() {
        UUID categoryId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 5);

        when(categoryRepository.existsById(categoryId)).thenReturn(false);

        assertThatThrownBy(() -> productService.getProductsByCategory(categoryId, pageable))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void shouldGetExpiredProducts() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findExpiredProducts(any(LocalDate.class), eq(pageable))).thenReturn(page);

        Page<Product> result = productService.getExpiredProducts(pageable);

        assertThat(result.getContent()).containsExactly(product);
        verify(productRepository).findExpiredProducts(any(LocalDate.class), eq(pageable));
    }

    @Test
    void shouldGetProductsExpiringSoonWithDefaultWindow() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<Product> page = new PageImpl<>(List.of(product));
        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);

        when(productRepository.findProductsExpiringBetween(any(LocalDate.class), any(LocalDate.class), eq(pageable)))
                .thenReturn(page);

        productService.getProductsExpiringSoon(null, pageable);

        verify(productRepository).findProductsExpiringBetween(startCaptor.capture(), endCaptor.capture(), eq(pageable));
        assertThat(endCaptor.getValue()).isEqualTo(startCaptor.getValue().plusDays(30));
    }

    @Test
    void shouldSearchProducts() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.searchByName("term", pageable)).thenReturn(page);

        Page<Product> result = productService.searchProducts("term", pageable);

        assertThat(result.getContent()).containsExactly(product);
        verify(productRepository).searchByName("term", pageable);
    }

    @Test
    void shouldGetProductById() {
        UUID id = product.getId();
        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        Product result = productService.getProductById(id);

        assertThat(result).isEqualTo(product);
        verify(productRepository).findById(id);
    }

    @Test
    void shouldThrowWhenProductByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(id))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("Product not found with id: " + id);
    }

    @Test
    void shouldGetProductByName() {
        when(productRepository.findByNameAndActiveTrue("Product")).thenReturn(Optional.of(product));

        Product result = productService.getProductByName("Product");

        assertThat(result).isEqualTo(product);
        verify(productRepository).findByNameAndActiveTrue("Product");
    }

    @Test
    void shouldThrowWhenProductByNameNotFound() {
        when(productRepository.findByNameAndActiveTrue("Unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductByName("Unknown"))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("Product not found with name: Unknown");
    }

    @Test
    void shouldUpdateProductFields() {
        UUID id = product.getId();
        Brand newBrand = new Brand();
        newBrand.setId(UUID.randomUUID());
        newBrand.setActive(true);

        Category newCategory = new Category();
        newCategory.setId(UUID.randomUUID());
        newCategory.setActive(true);

        UpdateProductRequest request = new UpdateProductRequest(
                "Updated", "Updated desc", newBrand.getId(), newCategory.getId(), 300L, LocalDate.now().plusDays(40)
        );

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productRepository.existsByNameAndActiveTrueAndIdNot(request.getName(), id)).thenReturn(false);
        when(brandRepository.findById(newBrand.getId())).thenReturn(Optional.of(newBrand));
        when(categoryRepository.findById(newCategory.getId())).thenReturn(Optional.of(newCategory));
        when(productRepository.save(product)).thenReturn(product);

        Product result = productService.updateProduct(id, request);

        assertThat(result.getName()).isEqualTo("Updated");
        assertThat(result.getDescription()).isEqualTo("Updated desc");
        assertThat(result.getBasePrice()).isEqualTo(300L);
        assertThat(result.getExpiryDate()).isEqualTo(request.getExpiryDate());
        assertThat(result.getBrand()).isEqualTo(newBrand);
        assertThat(result.getCategory()).isEqualTo(newCategory);
        verify(productRepository).save(product);
    }

    @Test
    void shouldThrowWhenUpdatingToExistingName() {
        UUID id = product.getId();
        UpdateProductRequest request = new UpdateProductRequest("New Name", null, null, null, null, null);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productRepository.existsByNameAndActiveTrueAndIdNot(request.getName(), id)).thenReturn(true);

        assertThatThrownBy(() -> productService.updateProduct(id, request))
                .isInstanceOf(ProductAlreadyExistsException.class)
                .hasMessageContaining("Product already exists with name: New Name");
    }

    @Test
    void shouldThrowWhenNewBrandNotFoundOnUpdate() {
        UUID id = product.getId();
        UUID newBrandId = UUID.randomUUID();
        UpdateProductRequest request = new UpdateProductRequest(null, null, newBrandId, null, null, null);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(brandRepository.findById(newBrandId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(id, request))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void shouldThrowWhenNewBrandInactiveOnUpdate() {
        UUID id = product.getId();
        Brand inactiveBrand = new Brand();
        inactiveBrand.setId(UUID.randomUUID());
        inactiveBrand.setActive(false);
        UpdateProductRequest request = new UpdateProductRequest(null, null, inactiveBrand.getId(), null, null, null);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(brandRepository.findById(inactiveBrand.getId())).thenReturn(Optional.of(inactiveBrand));

        assertThatThrownBy(() -> productService.updateProduct(id, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Brand is not active");
    }

    @Test
    void shouldThrowWhenNewCategoryNotFoundOnUpdate() {
        UUID id = product.getId();
        UUID newCategoryId = UUID.randomUUID();
        UpdateProductRequest request = new UpdateProductRequest(null, null, null, newCategoryId, null, null);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(newCategoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(id, request))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void shouldThrowWhenNewCategoryInactiveOnUpdate() {
        UUID id = product.getId();
        Category inactiveCategory = new Category();
        inactiveCategory.setId(UUID.randomUUID());
        inactiveCategory.setActive(false);
        UpdateProductRequest request = new UpdateProductRequest(null, null, null, inactiveCategory.getId(), null, null);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(inactiveCategory.getId())).thenReturn(Optional.of(inactiveCategory));

        assertThatThrownBy(() -> productService.updateProduct(id, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category is not active");
    }

    @Test
    void shouldDeactivateProductOnDelete() {
        UUID id = product.getId();
        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        productService.deleteProduct(id);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void shouldActivateProductWhenAssociationsActive() {
        UUID id = product.getId();
        product.setActive(false);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        productService.activateProduct(id);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void shouldThrowWhenActivatingProductWithInactiveBrand() {
        UUID id = product.getId();
        product.getBrand().setActive(false);
        product.setActive(false);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.activateProduct(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brand is not active");
    }

    @Test
    void shouldThrowWhenActivatingProductWithInactiveCategory() {
        UUID id = product.getId();
        product.getCategory().setActive(false);
        product.setActive(false);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.activateProduct(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category is not active");
    }
}
