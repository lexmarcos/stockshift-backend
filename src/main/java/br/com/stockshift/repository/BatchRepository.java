package br.com.stockshift.repository;

import br.com.stockshift.dto.warehouse.ProductWithStockProjection;
import br.com.stockshift.model.entity.Batch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchRepository extends JpaRepository<Batch, UUID> {

    @Query("SELECT b FROM Batch b WHERE b.tenantId = :tenantId")
    List<Batch> findAllByTenantId(UUID tenantId);

    @Query("SELECT b FROM Batch b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<Batch> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT b FROM Batch b WHERE b.tenantId = :tenantId AND b.batchCode = :batchCode")
    Optional<Batch> findByTenantIdAndBatchCode(UUID tenantId, String batchCode);

    @Query("SELECT b FROM Batch b WHERE b.warehouse.id = :warehouseId AND b.tenantId = :tenantId")
    List<Batch> findByWarehouseIdAndTenantId(UUID warehouseId, UUID tenantId);

    @Query("SELECT b FROM Batch b WHERE b.product.id = :productId AND b.tenantId = :tenantId")
    List<Batch> findByProductIdAndTenantId(UUID productId, UUID tenantId);

    @Query("SELECT b FROM Batch b WHERE b.product.id = :productId AND b.warehouse.id = :warehouseId AND b.tenantId = :tenantId")
    List<Batch> findByProductIdAndWarehouseIdAndTenantId(UUID productId, UUID warehouseId, UUID tenantId);

    @Query("SELECT b FROM Batch b WHERE b.expirationDate IS NOT NULL AND b.expirationDate BETWEEN :startDate AND :endDate AND b.tenantId = :tenantId ORDER BY b.expirationDate ASC")
    List<Batch> findExpiringBatches(LocalDate startDate, LocalDate endDate, UUID tenantId);

    @Query("SELECT b FROM Batch b WHERE b.quantity <= :threshold AND b.tenantId = :tenantId")
    List<Batch> findLowStock(Integer threshold, UUID tenantId);

    @Query(value = """
        SELECT p.id as id,
               p.name as name,
               p.sku as sku,
               p.barcode as barcode,
               p.barcodeType as barcodeType,
               p.description as description,
               p.category as category,
               p.brand as brand,
               p.isKit as isKit,
               p.attributes as attributes,
               p.hasExpiration as hasExpiration,
               p.active as active,
               COALESCE(SUM(b.quantity), 0) as totalQuantity,
               p.createdAt as createdAt,
               p.updatedAt as updatedAt
        FROM Batch b
        JOIN b.product p
        LEFT JOIN p.category
        LEFT JOIN p.brand
        WHERE b.warehouse.id = :warehouseId
          AND b.tenantId = :tenantId
          AND p.tenantId = :tenantId
          AND p.deletedAt IS NULL
        GROUP BY p.id, p.name, p.sku, p.barcode, p.barcodeType,
                 p.description, p.category, p.brand, p.isKit,
                 p.attributes, p.hasExpiration, p.active,
                 p.createdAt, p.updatedAt
        """,
        countQuery = """
        SELECT COUNT(DISTINCT p.id)
        FROM Batch b
        JOIN b.product p
        WHERE b.warehouse.id = :warehouseId
          AND b.tenantId = :tenantId
          AND p.tenantId = :tenantId
          AND p.deletedAt IS NULL
        """)
    Page<ProductWithStockProjection> findProductsWithStockByWarehouse(
        @Param("warehouseId") UUID warehouseId,
        @Param("tenantId") UUID tenantId,
        Pageable pageable
    );

    @Modifying
    @Query("UPDATE Batch b SET b.deletedAt = CURRENT_TIMESTAMP " +
           "WHERE b.productId = :productId " +
           "AND b.warehouseId = :warehouseId " +
           "AND b.tenantId = :tenantId " +
           "AND b.deletedAt IS NULL")
    int softDeleteByProductAndWarehouse(
        @Param("productId") UUID productId,
        @Param("warehouseId") UUID warehouseId,
        @Param("tenantId") UUID tenantId
    );
}
