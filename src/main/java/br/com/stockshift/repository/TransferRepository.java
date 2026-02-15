package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Transfer;
import br.com.stockshift.model.enums.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    @Query("SELECT t FROM Transfer t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<Transfer> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM Transfer t WHERE t.tenantId = :tenantId")
    Page<Transfer> findAllByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT t FROM Transfer t WHERE t.tenantId = :tenantId AND t.status = :status")
    Page<Transfer> findAllByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") TransferStatus status, Pageable pageable);

    @Query("SELECT t FROM Transfer t WHERE t.tenantId = :tenantId AND t.sourceWarehouseId = :warehouseId")
    Page<Transfer> findAllByTenantIdAndSourceWarehouseId(@Param("tenantId") UUID tenantId, @Param("warehouseId") UUID warehouseId, Pageable pageable);

    @Query("SELECT t FROM Transfer t WHERE t.tenantId = :tenantId AND t.destinationWarehouseId = :warehouseId")
    Page<Transfer> findAllByTenantIdAndDestinationWarehouseId(@Param("tenantId") UUID tenantId, @Param("warehouseId") UUID warehouseId, Pageable pageable);

    @Query("SELECT MAX(t.code) FROM Transfer t WHERE t.tenantId = :tenantId AND t.code LIKE CONCAT(:prefix, '%')")
    String findLatestCodeByTenantIdAndCodePrefix(@Param("tenantId") UUID tenantId, @Param("prefix") String prefix);

    @Query("SELECT COUNT(t) FROM Transfer t WHERE t.tenantId = :tenantId AND t.code LIKE CONCAT(:prefix, '%')")
    long countByTenantIdAndCodePrefix(@Param("tenantId") UUID tenantId, @Param("prefix") String prefix);
}
