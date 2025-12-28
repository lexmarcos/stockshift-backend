package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    @Query("SELECT w FROM Warehouse w WHERE w.tenantId = :tenantId")
    List<Warehouse> findAllByTenantId(UUID tenantId);

    @Query("SELECT w FROM Warehouse w WHERE w.tenantId = :tenantId AND w.id = :id")
    Optional<Warehouse> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT w FROM Warehouse w WHERE w.tenantId = :tenantId AND w.isActive = :isActive")
    List<Warehouse> findByTenantIdAndIsActive(UUID tenantId, Boolean isActive);

    @Query("SELECT w FROM Warehouse w WHERE w.tenantId = :tenantId AND w.name = :name")
    Optional<Warehouse> findByTenantIdAndName(UUID tenantId, String name);
}
