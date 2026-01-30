package br.com.stockshift.repository;

import br.com.stockshift.model.entity.NewTransferDiscrepancy;
import br.com.stockshift.model.enums.DiscrepancyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NewTransferDiscrepancyRepository extends JpaRepository<NewTransferDiscrepancy, UUID> {

    List<NewTransferDiscrepancy> findByTransferId(UUID transferId);

    List<NewTransferDiscrepancy> findByTransferIdAndStatus(UUID transferId, DiscrepancyStatus status);

    @Query("SELECT d FROM NewTransferDiscrepancy d WHERE d.tenantId = :tenantId AND d.status = 'PENDING_RESOLUTION'")
    List<NewTransferDiscrepancy> findPendingByTenantId(@Param("tenantId") UUID tenantId);

    List<NewTransferDiscrepancy> findByTransferItemId(UUID transferItemId);
}
