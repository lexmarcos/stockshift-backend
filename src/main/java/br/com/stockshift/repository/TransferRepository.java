package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.enums.TransferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByTenantIdAndId(UUID tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transfer t WHERE t.id = :id")
    Optional<Transfer> findByIdForUpdate(@Param("id") UUID id);

    Page<Transfer> findByTenantIdAndSourceWarehouseId(UUID tenantId, UUID warehouseId, Pageable pageable);

    Page<Transfer> findByTenantIdAndDestinationWarehouseId(UUID tenantId, UUID warehouseId, Pageable pageable);

    Page<Transfer> findByTenantIdAndStatus(UUID tenantId, TransferStatus status, Pageable pageable);

    @Query("SELECT t FROM Transfer t WHERE t.tenantId = :tenantId " +
           "AND (t.sourceWarehouseId = :warehouseId OR t.destinationWarehouseId = :warehouseId)")
    Page<Transfer> findByTenantIdAndWarehouseId(
        @Param("tenantId") UUID tenantId,
        @Param("warehouseId") UUID warehouseId,
        Pageable pageable
    );

    boolean existsByTenantIdAndTransferCode(UUID tenantId, String transferCode);
}
