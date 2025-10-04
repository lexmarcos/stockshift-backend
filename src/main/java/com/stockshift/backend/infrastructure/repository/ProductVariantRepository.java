package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.product.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findBySku(String sku);
    
    Optional<ProductVariant> findByGtin(String gtin);
    
    boolean existsBySku(String sku);
    
    boolean existsByGtin(String gtin);
    
    boolean existsByGtinAndIdNot(String gtin, UUID id);
    
    boolean existsByProductIdAndAttributesHash(UUID productId, String attributesHash);
    
    Page<ProductVariant> findByActiveTrue(Pageable pageable);
    
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId")
    Page<ProductVariant> findAllByProductId(@Param("productId") UUID productId, Pageable pageable);
}
