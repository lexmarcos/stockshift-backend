package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.attribute.AttributeDefinition;
import com.stockshift.backend.domain.attribute.AttributeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, UUID> {

    // Legacy methods (kept for backward compatibility)
    Optional<AttributeDefinition> findByNameAndActiveTrue(String name);
    boolean existsByNameAndActiveTrue(String name);
    boolean existsByNameAndActiveTrueAndIdNot(String name, UUID id);
    Page<AttributeDefinition> findAllByActiveTrue(Pageable pageable);

    // New methods using code and status
    Optional<AttributeDefinition> findByCode(String code);
    boolean existsByCode(String code);
    Page<AttributeDefinition> findAllByStatus(AttributeStatus status, Pageable pageable);
}
