package br.com.stockshift.repository;

import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.enums.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Optional<StockMovement> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT sm FROM StockMovement sm WHERE sm.tenantId = :tenantId AND sm.warehouseId = :warehouseId ORDER BY sm.createdAt DESC")
    Page<StockMovement> findByTenantIdAndWarehouseId(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            Pageable pageable);

    @Query("SELECT sm FROM StockMovement sm WHERE sm.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND (CAST(:type AS string) IS NULL OR sm.type = :type) " +
            "AND (CAST(:dateFrom AS string) IS NULL OR sm.createdAt >= :dateFrom) " +
            "AND (CAST(:dateTo AS string) IS NULL OR sm.createdAt <= :dateTo) " +
            "ORDER BY sm.createdAt DESC")
    Page<StockMovement> findWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("type") StockMovementType type,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);

    @Query("SELECT sm FROM StockMovement sm LEFT JOIN FETCH sm.items WHERE sm.tenantId = :tenantId " +
            "AND sm.warehouseId IN :warehouseIds " +
            "AND (CAST(:dateFrom AS string) IS NULL OR sm.createdAt >= :dateFrom) " +
            "AND (CAST(:dateTo AS string) IS NULL OR sm.createdAt <= :dateTo)")
    List<StockMovement> findForWarehouseSummary(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseIds") List<UUID> warehouseIds,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT sm.code FROM StockMovement sm WHERE sm.tenantId = :tenantId AND sm.code LIKE :prefix% ORDER BY sm.code DESC LIMIT 1")
    String findLatestCodeByTenantIdAndCodePrefix(@Param("tenantId") UUID tenantId, @Param("prefix") String prefix);

    @Query("SELECT COUNT(sm) FROM StockMovement sm WHERE sm.tenantId = :tenantId AND sm.code LIKE :prefix%")
    long countByTenantIdAndCodePrefix(@Param("tenantId") UUID tenantId, @Param("prefix") String prefix);

    @Query("SELECT sm FROM StockMovement sm JOIN FETCH sm.items i WHERE sm.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND (:productId IS NULL OR i.productId = :productId) " +
            "AND (CAST(:type AS string) IS NULL OR sm.type = :type) " +
            "AND (CAST(:dateFrom AS string) IS NULL OR sm.createdAt >= :dateFrom) " +
            "AND (CAST(:dateTo AS string) IS NULL OR sm.createdAt <= :dateTo) " +
            "ORDER BY sm.createdAt DESC")
    Page<StockMovement> findExtract(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("productId") UUID productId,
            @Param("type") StockMovementType type,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);
}
