package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Sale;
import br.com.stockshift.model.enums.PaymentMethod;
import br.com.stockshift.model.enums.SaleStatus;
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
public interface SaleRepository extends JpaRepository<Sale, UUID> {

    @Query("SELECT s FROM Sale s LEFT JOIN FETCH s.items WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<Sale> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT s FROM Sale s WHERE s.tenantId = :tenantId " +
            "AND (:warehouseId IS NULL OR s.warehouseId = :warehouseId) " +
            "AND (CAST(:paymentMethod AS string) IS NULL OR s.paymentMethod = :paymentMethod) " +
            "AND (CAST(:status AS string) IS NULL OR s.status = :status) " +
            "AND (CAST(:dateFrom AS string) IS NULL OR s.createdAt >= :dateFrom) " +
            "AND (CAST(:dateTo AS string) IS NULL OR s.createdAt <= :dateTo) " +
            "ORDER BY s.createdAt DESC")
    Page<Sale> findWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("paymentMethod") PaymentMethod paymentMethod,
            @Param("status") SaleStatus status,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);

    @Query("SELECT s.code FROM Sale s WHERE s.tenantId = :tenantId AND s.code LIKE :prefix ORDER BY s.code DESC LIMIT 1")
    String findLatestCodeByTenantIdAndCodePrefix(@Param("tenantId") UUID tenantId, @Param("prefix") String prefix);

    @Query("SELECT COUNT(s) FROM Sale s WHERE s.tenantId = :tenantId AND s.code LIKE :prefix")
    long countByTenantIdAndCodePrefix(@Param("tenantId") UUID tenantId, @Param("prefix") String prefix);

    @Query("SELECT COUNT(s), COALESCE(SUM(s.total), 0) FROM Sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND (:warehouseId IS NULL OR s.warehouseId = :warehouseId) " +
           "AND s.status = 'COMPLETED' " +
           "AND s.createdAt >= :from AND s.createdAt < :to")
    List<Object[]> countAndRevenueByPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT CAST(s.createdAt AS date), COUNT(s), COALESCE(SUM(s.total), 0) " +
           "FROM Sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND (:warehouseId IS NULL OR s.warehouseId = :warehouseId) " +
           "AND s.status = 'COMPLETED' " +
           "AND s.createdAt >= :from AND s.createdAt < :to " +
           "GROUP BY CAST(s.createdAt AS date) " +
           "ORDER BY CAST(s.createdAt AS date) ASC")
    List<Object[]> dailySalesInPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
