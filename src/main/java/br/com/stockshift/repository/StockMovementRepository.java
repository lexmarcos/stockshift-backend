package br.com.stockshift.repository;

import br.com.stockshift.model.entity.StockMovement;
import br.com.stockshift.model.enums.MovementStatus;
import br.com.stockshift.model.enums.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId ORDER BY m.createdAt DESC")
    List<StockMovement> findAllByTenantId(UUID tenantId);

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId AND m.id = :id")
    Optional<StockMovement> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId AND m.movementType = :movementType ORDER BY m.createdAt DESC")
    List<StockMovement> findByTenantIdAndMovementType(UUID tenantId, MovementType movementType);

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId AND m.status = :status ORDER BY m.createdAt DESC")
    List<StockMovement> findByTenantIdAndStatus(UUID tenantId, MovementStatus status);

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId AND (m.sourceWarehouse.id = :warehouseId OR m.destinationWarehouse.id = :warehouseId) ORDER BY m.createdAt DESC")
    List<StockMovement> findByTenantIdAndWarehouse(UUID tenantId, UUID warehouseId);

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId AND m.user.id = :userId ORDER BY m.createdAt DESC")
    List<StockMovement> findByTenantIdAndUser(UUID tenantId, UUID userId);

    @Query("SELECT m FROM StockMovement m WHERE m.tenantId = :tenantId AND m.createdAt BETWEEN :startDate AND :endDate ORDER BY m.createdAt DESC")
    List<StockMovement> findByTenantIdAndDateRange(UUID tenantId, LocalDateTime startDate, LocalDateTime endDate);
}
