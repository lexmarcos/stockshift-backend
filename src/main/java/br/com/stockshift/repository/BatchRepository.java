package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}
