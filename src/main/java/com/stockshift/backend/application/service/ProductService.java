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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public Product createProduct(CreateProductRequest request) {
        if (productRepository.existsByNameAndActiveTrue(request.getName())) {
            throw new ProductAlreadyExistsException(request.getName());
        }

        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBasePrice(request.getBasePrice());
        product.setExpiryDate(request.getExpiryDate());

        // Set brand if provided
        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new BrandNotFoundException(request.getBrandId()));
            
            if (!brand.getActive()) {
                throw new IllegalArgumentException("Brand is not active");
            }
            
            product.setBrand(brand);
        }

        // Set category if provided
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new CategoryNotFoundException(request.getCategoryId()));
            
            if (!category.getActive()) {
                throw new IllegalArgumentException("Category is not active");
            }
            
            product.setCategory(category);
        }

        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> getActiveProducts(Pageable pageable) {
        return productRepository.findAllByActiveTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> getProductsByBrand(UUID brandId, Pageable pageable) {
        // Verify brand exists
        if (!brandRepository.existsById(brandId)) {
            throw new BrandNotFoundException(brandId);
        }
        return productRepository.findByBrandId(brandId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> getProductsByCategory(UUID categoryId, Pageable pageable) {
        // Verify category exists
        if (!categoryRepository.existsById(categoryId)) {
            throw new CategoryNotFoundException(categoryId);
        }
        return productRepository.findByCategoryId(categoryId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> getExpiredProducts(Pageable pageable) {
        return productRepository.findExpiredProducts(LocalDate.now(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> getProductsExpiringSoon(Integer days, Pageable pageable) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days != null ? days : 30);
        return productRepository.findProductsExpiringBetween(startDate, endDate, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> searchProducts(String search, Pageable pageable) {
        return productRepository.searchByName(search, pageable);
    }

    @Transactional(readOnly = true)
    public Product getProductById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Product getProductByName(String name) {
        return productRepository.findByNameAndActiveTrue(name)
                .orElseThrow(() -> new ProductNotFoundException(name));
    }

    @Transactional
    public Product updateProduct(UUID id, UpdateProductRequest request) {
        Product product = getProductById(id);

        if (request.getName() != null && !request.getName().equals(product.getName())) {
            if (productRepository.existsByNameAndActiveTrueAndIdNot(request.getName(), id)) {
                throw new ProductAlreadyExistsException(request.getName());
            }
            product.setName(request.getName());
        }

        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }

        if (request.getBasePrice() != null) {
            product.setBasePrice(request.getBasePrice());
        }

        if (request.getExpiryDate() != null) {
            product.setExpiryDate(request.getExpiryDate());
        }

        // Update brand if provided
        if (request.getBrandId() != null) {
            if (!request.getBrandId().equals(product.getBrand() != null ? product.getBrand().getId() : null)) {
                Brand brand = brandRepository.findById(request.getBrandId())
                        .orElseThrow(() -> new BrandNotFoundException(request.getBrandId()));
                
                if (!brand.getActive()) {
                    throw new IllegalArgumentException("Brand is not active");
                }
                
                product.setBrand(brand);
            }
        }

        // Update category if provided
        if (request.getCategoryId() != null) {
            if (!request.getCategoryId().equals(product.getCategory() != null ? product.getCategory().getId() : null)) {
                Category category = categoryRepository.findById(request.getCategoryId())
                        .orElseThrow(() -> new CategoryNotFoundException(request.getCategoryId()));
                
                if (!category.getActive()) {
                    throw new IllegalArgumentException("Category is not active");
                }
                
                product.setCategory(category);
            }
        }

        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = getProductById(id);
        product.setActive(false);
        productRepository.save(product);
    }

    @Transactional
    public void activateProduct(UUID id) {
        Product product = getProductById(id);
        
        // Validate brand and category are active if set
        if (product.getBrand() != null && !product.getBrand().getActive()) {
            throw new IllegalArgumentException("Cannot activate product: brand is not active");
        }
        
        if (product.getCategory() != null && !product.getCategory().getActive()) {
            throw new IllegalArgumentException("Cannot activate product: category is not active");
        }
        
        product.setActive(true);
        productRepository.save(product);
    }
}
