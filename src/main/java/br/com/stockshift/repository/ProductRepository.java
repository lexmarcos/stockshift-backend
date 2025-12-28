package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL")
    List<Product> findAllByTenantId(UUID tenantId);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.id = :id AND p.deletedAt IS NULL")
    Optional<Product> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.active = :active AND p.deletedAt IS NULL")
    List<Product> findByTenantIdAndActive(UUID tenantId, Boolean active);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.category.id = :categoryId AND p.deletedAt IS NULL")
    List<Product> findByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "p.deletedAt IS NULL")
    List<Product> searchByTenantId(@Param("tenantId") UUID tenantId, @Param("search") String search);

    @Query("SELECT p FROM Product p WHERE p.barcode = :barcode AND p.tenantId = :tenantId AND p.deletedAt IS NULL")
    Optional<Product> findByBarcodeAndTenantId(String barcode, UUID tenantId);

    @Query("SELECT p FROM Product p WHERE p.sku = :sku AND p.tenantId = :tenantId AND p.deletedAt IS NULL")
    Optional<Product> findBySkuAndTenantId(String sku, UUID tenantId);
}
