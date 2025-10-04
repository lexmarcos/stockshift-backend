package com.stockshift.backend.application.service;

import com.stockshift.backend.api.dto.brand.CreateBrandRequest;
import com.stockshift.backend.api.dto.brand.UpdateBrandRequest;
import com.stockshift.backend.api.exception.BrandAlreadyExistsException;
import com.stockshift.backend.api.exception.BrandNotFoundException;
import com.stockshift.backend.domain.brand.Brand;
import com.stockshift.backend.infrastructure.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public Brand createBrand(CreateBrandRequest request) {
        // Verify if brand name already exists
        if (brandRepository.existsByName(request.getName())) {
            throw new BrandAlreadyExistsException("Brand already exists: " + request.getName());
        }

        Brand brand = new Brand();
        brand.setName(request.getName());
        brand.setDescription(request.getDescription());
        brand.setActive(true);

        return brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public Brand getBrandById(UUID id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new BrandNotFoundException("Brand not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Brand getBrandByName(String name) {
        return brandRepository.findByName(name)
                .orElseThrow(() -> new BrandNotFoundException("Brand not found with name: " + name));
    }

    @Transactional(readOnly = true)
    public Page<Brand> getAllBrands(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Brand> getActiveBrands(Pageable pageable) {
        return brandRepository.findByActiveTrue(pageable);
    }

    @Transactional
    public Brand updateBrand(UUID id, UpdateBrandRequest request) {
        Brand brand = getBrandById(id);

        if (request.getName() != null) {
            // Check if name is already used by another brand
            if (brandRepository.existsByName(request.getName()) && 
                !brand.getName().equals(request.getName())) {
                throw new BrandAlreadyExistsException("Brand already exists: " + request.getName());
            }
            brand.setName(request.getName());
        }

        if (request.getDescription() != null) {
            brand.setDescription(request.getDescription());
        }

        if (request.getActive() != null) {
            brand.setActive(request.getActive());
        }

        return brandRepository.save(brand);
    }

    @Transactional
    public void deleteBrand(UUID id) {
        Brand brand = getBrandById(id);
        brand.setActive(false);
        brandRepository.save(brand);
    }

    @Transactional
    public void activateBrand(UUID id) {
        Brand brand = getBrandById(id);
        brand.setActive(true);
        brandRepository.save(brand);
    }
}
