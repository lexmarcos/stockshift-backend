package br.com.stockshift.repository;

import br.com.stockshift.model.entity.ProductPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductPromptRepository extends JpaRepository<ProductPrompt, UUID> {

    @Query("SELECT p FROM ProductPrompt p WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<ProductPrompt> findAllActiveByTenantId(UUID tenantId);

    @Query("SELECT p FROM ProductPrompt p WHERE p.tenantId = :tenantId AND p.id = :id AND p.deletedAt IS NULL")
    Optional<ProductPrompt> findActiveByTenantIdAndId(UUID tenantId, UUID id);
}
