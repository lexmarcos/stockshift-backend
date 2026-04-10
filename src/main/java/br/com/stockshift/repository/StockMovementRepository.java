package br.com.stockshift.repository;

import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.enums.StockMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
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

    @Query("SELECT sm FROM StockMovement sm WHERE sm.tenantId = :tenantId " +
            "AND sm.warehouseId IN :warehouseIds " +
            "AND (CAST(:type AS string) IS NULL OR sm.type = :type) " +
            "AND (CAST(:dateFrom AS string) IS NULL OR sm.createdAt >= :dateFrom) " +
            "AND (CAST(:dateTo AS string) IS NULL OR sm.createdAt <= :dateTo) " +
            "ORDER BY sm.createdAt DESC")
    Page<StockMovement> findWithFiltersByWarehouseIds(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
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

    @Query("SELECT sm.type, sm.direction, COALESCE(SUM(smi.quantity), 0) " +
            "FROM StockMovement sm JOIN sm.items smi " +
            "WHERE sm.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate " +
            "GROUP BY sm.type, sm.direction")
    List<Object[]> sumMovementsByTypeAndPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(sm) FROM StockMovement sm " +
            "WHERE sm.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND sm.createdAt >= :startOfDay AND sm.createdAt < :endOfDay")
    long countTodayMovements(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT CAST(sm.createdAt AS LocalDate), sm.direction, COALESCE(SUM(smi.quantity), 0), COUNT(DISTINCT sm) " +
            "FROM StockMovement sm JOIN sm.items smi " +
            "WHERE sm.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate " +
            "GROUP BY CAST(sm.createdAt AS LocalDate), sm.direction " +
            "ORDER BY CAST(sm.createdAt AS LocalDate)")
    List<Object[]> getDailyMovementTrend(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT sm.type, smi.productName, smi.quantity, sm.createdAt, smi.batchId " +
            "FROM StockMovement sm JOIN sm.items smi " +
            "WHERE sm.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND sm.type IN :lossTypes " +
            "AND sm.createdAt >= :since " +
            "ORDER BY sm.createdAt DESC")
    List<Object[]> findRecentLosses(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("lossTypes") List<StockMovementType> lossTypes,
            @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(smi.quantity), 0) FROM StockMovement sm JOIN sm.items smi " +
            "WHERE sm.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND sm.direction = br.com.stockshift.model.enums.MovementDirection.OUT " +
            "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate")
    BigDecimal sumOutQuantityForPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT sm.type, COUNT(sm) FROM StockMovement sm " +
            "WHERE sm.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate " +
            "GROUP BY sm.type")
    List<Object[]> countMovementsByTypeAndPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT sm.type, COUNT(sm) FROM StockMovement sm " +
            "WHERE sm.tenantId = :tenantId " +
            "AND sm.warehouseId IN :warehouseIds " +
            "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate " +
            "GROUP BY sm.type")
    List<Object[]> countMovementsByTypeAndPeriodByWarehouseIds(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(DISTINCT sm.referenceId) FROM StockMovement sm " +
            "WHERE sm.tenantId = :tenantId " +
            "AND sm.referenceType = 'TRANSFER' " +
            "AND sm.referenceId IS NOT NULL " +
            "AND sm.type IN :transferTypes " +
            "AND (:warehouseId IS NULL OR sm.warehouseId = :warehouseId) " +
            "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate")
    long countDistinctTransferReferencesByPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("transferTypes") Collection<StockMovementType> transferTypes);

    @Query("SELECT COUNT(DISTINCT sm.referenceId) FROM StockMovement sm " +
            "WHERE sm.tenantId = :tenantId " +
            "AND sm.referenceType = 'TRANSFER' " +
            "AND sm.referenceId IS NOT NULL " +
            "AND sm.type IN :transferTypes " +
            "AND sm.warehouseId IN :warehouseIds " +
            "AND sm.createdAt >= :startDate AND sm.createdAt < :endDate")
    long countDistinctTransferReferencesByPeriodByWarehouseIds(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("transferTypes") Collection<StockMovementType> transferTypes);
}
