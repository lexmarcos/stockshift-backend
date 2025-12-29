package br.com.stockshift.service;

import br.com.stockshift.dto.brand.BrandRequest;
import br.com.stockshift.dto.brand.BrandResponse;
import br.com.stockshift.exception.BusinessException;
import br.com.stockshift.exception.ResourceNotFoundException;
import br.com.stockshift.model.entity.Brand;
import br.com.stockshift.repository.BrandRepository;
import br.com.stockshift.repository.ProductRepository;
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
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Transactional
    public BrandResponse create(BrandRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Validate unique name within tenant
        if (brandRepository.existsByNameAndTenantIdAndDeletedAtIsNull(request.getName(), tenantId)) {
            throw new BusinessException("Brand with name '" + request.getName() + "' already exists");
        }

        Brand brand = new Brand();
        brand.setTenantId(tenantId);
        brand.setName(request.getName());
        brand.setLogoUrl(request.getLogoUrl());

        Brand saved = brandRepository.save(brand);
        log.info("Created brand {} for tenant {}", saved.getId(), tenantId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> findAll() {
        UUID tenantId = TenantContext.getTenantId();
        return brandRepository.findAllByTenantIdAndDeletedAtIsNull(tenantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BrandResponse findById(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        Brand brand = findBrandByIdAndTenant(id, tenantId);
        return mapToResponse(brand);
    }

    @Transactional
    public BrandResponse update(UUID id, BrandRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        Brand brand = findBrandByIdAndTenant(id, tenantId);

        // Validate unique name within tenant (exclude current brand)
        if (brandRepository.existsByNameAndTenantIdAndDeletedAtIsNullAndIdNot(request.getName(), tenantId, id)) {
            throw new BusinessException("Brand with name '" + request.getName() + "' already exists");
        }

        brand.setName(request.getName());
        brand.setLogoUrl(request.getLogoUrl());

        Brand updated = brandRepository.save(brand);
        log.info("Updated brand {} for tenant {}", id, tenantId);

        return mapToResponse(updated);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        Brand brand = findBrandByIdAndTenant(id, tenantId);

        // Validate no products exist with this brand
        if (productRepository.existsByBrandIdAndDeletedAtIsNull(id)) {
            throw new BusinessException("Cannot delete brand with associated products");
        }

        // Soft delete
        brand.setDeletedAt(LocalDateTime.now());
        brandRepository.save(brand);

        log.info("Soft deleted brand {} for tenant {}", id, tenantId);
    }

    private Brand findBrandByIdAndTenant(UUID id, UUID tenantId) {
        return brandRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", "id", id));
    }

    private BrandResponse mapToResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .logoUrl(brand.getLogoUrl())
                .createdAt(brand.getCreatedAt())
                .updatedAt(brand.getUpdatedAt())
                .build();
    }
}
