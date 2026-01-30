package br.com.stockshift.repository;

import br.com.stockshift.model.entity.InventoryLedger;
import br.com.stockshift.model.enums.LedgerEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, UUID> {

    List<InventoryLedger> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

    List<InventoryLedger> findByTenantIdAndBatchId(UUID tenantId, UUID batchId);

    Page<InventoryLedger> findByTenantIdAndWarehouseId(UUID tenantId, UUID warehouseId, Pageable pageable);

    @Query("SELECT l FROM InventoryLedger l WHERE l.tenantId = :tenantId " +
           "AND l.referenceType = :referenceType AND l.referenceId = :referenceId " +
           "ORDER BY l.createdAt ASC")
    List<InventoryLedger> findByReference(
        @Param("tenantId") UUID tenantId,
        @Param("referenceType") String referenceType,
        @Param("referenceId") UUID referenceId
    );

    @Query("SELECT COUNT(l) FROM InventoryLedger l " +
           "WHERE l.referenceType = :referenceType AND l.referenceId = :referenceId " +
           "AND l.entryType = :entryType")
    long countByReferenceAndEntryType(
        @Param("referenceType") String referenceType,
        @Param("referenceId") UUID referenceId,
        @Param("entryType") LedgerEntryType entryType
    );
}
