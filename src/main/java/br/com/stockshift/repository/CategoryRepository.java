package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL")
    List<Category> findAllByTenantId(UUID tenantId);

    @Query("SELECT c FROM Category c WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL")
    Optional<Category> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT c FROM Category c WHERE c.parentCategory.id = :parentId AND c.deletedAt IS NULL")
    List<Category> findByParentCategoryId(UUID parentId);

    @Query("SELECT c FROM Category c WHERE LOWER(c.name) = LOWER(:name) AND c.tenantId = :tenantId AND c.deletedAt IS NULL")
    Optional<Category> findByNameIgnoreCaseAndTenantId(String name, UUID tenantId);

    @Query("SELECT c FROM Category c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL")
    List<Category> findByTenantIdAndDeletedAtIsNull(UUID tenantId);
}
