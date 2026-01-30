package br.com.stockshift.repository;

import br.com.stockshift.model.entity.TransferInTransit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferInTransitRepository extends JpaRepository<TransferInTransit, UUID> {

    List<TransferInTransit> findByTransferId(UUID transferId);

    List<TransferInTransit> findByTransferIdAndConsumedAtIsNull(UUID transferId);

    Optional<TransferInTransit> findByTransferItemId(UUID transferItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransferInTransit t WHERE t.transferItem.id = :transferItemId")
    Optional<TransferInTransit> findByTransferItemIdForUpdate(@Param("transferItemId") UUID transferItemId);

    @Query("SELECT t FROM TransferInTransit t WHERE t.tenantId = :tenantId AND t.consumedAt IS NULL")
    List<TransferInTransit> findPendingByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT SUM(t.quantity) FROM TransferInTransit t " +
           "WHERE t.tenantId = :tenantId AND t.consumedAt IS NULL")
    Integer sumPendingQuantityByTenantId(@Param("tenantId") UUID tenantId);
}
