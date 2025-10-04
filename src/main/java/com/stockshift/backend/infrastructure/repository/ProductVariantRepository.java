package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.product.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    // Legacy methods (kept for backward compatibility)
    Optional<ProductVariant> findBySkuAndActiveTrue(String sku);
    Optional<ProductVariant> findByGtinAndActiveTrue(String gtin);
    boolean existsBySkuAndActiveTrue(String sku);
    boolean existsBySkuAndActiveTrueAndIdNot(String sku, UUID id);
    boolean existsByGtinAndActiveTrue(String gtin);
    boolean existsByGtinAndActiveTrueAndIdNot(String gtin, UUID id);

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND pv.active = true")
    Page<ProductVariant> findByProductId(@Param("productId") UUID productId, Pageable pageable);

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId")
    Page<ProductVariant> findAllByProductId(@Param("productId") UUID productId, Pageable pageable);

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.active = true")
    Page<ProductVariant> findAllByActiveTrue(Pageable pageable);

    @Query("SELECT pv FROM ProductVariant pv JOIN pv.attributeValues av WHERE av.id IN :attributeValueIds AND pv.active = true GROUP BY pv HAVING COUNT(DISTINCT av.id) = :count")
    Page<ProductVariant> findByAttributeValues(@Param("attributeValueIds") List<UUID> attributeValueIds, @Param("count") long count, Pageable pageable);

    // New methods for enhanced attribute system
    Optional<ProductVariant> findBySku(String sku);
    Optional<ProductVariant> findByGtin(String gtin);
    boolean existsBySku(String sku);
    boolean existsByGtin(String gtin);
    boolean existsByGtinAndIdNot(String gtin, UUID id);
    boolean existsByProductIdAndAttributesHash(UUID productId, String attributesHash);
    Page<ProductVariant> findByActiveTrue(Pageable pageable);
}
