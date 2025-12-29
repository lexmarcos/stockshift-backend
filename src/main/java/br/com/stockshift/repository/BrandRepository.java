package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BrandRepository extends JpaRepository<Brand, UUID> {

    @Query("SELECT b FROM Brand b WHERE b.tenantId = :tenantId AND b.id = :id AND b.deletedAt IS NULL")
    Optional<Brand> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT b FROM Brand b WHERE b.tenantId = :tenantId AND b.deletedAt IS NULL")
    List<Brand> findAllByTenantIdAndDeletedAtIsNull(UUID tenantId);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Brand b WHERE b.name = :name AND b.tenantId = :tenantId AND b.deletedAt IS NULL")
    boolean existsByNameAndTenantIdAndDeletedAtIsNull(String name, UUID tenantId);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Brand b WHERE b.name = :name AND b.tenantId = :tenantId AND b.deletedAt IS NULL AND b.id <> :id")
    boolean existsByNameAndTenantIdAndDeletedAtIsNullAndIdNot(String name, UUID tenantId, UUID id);
}
