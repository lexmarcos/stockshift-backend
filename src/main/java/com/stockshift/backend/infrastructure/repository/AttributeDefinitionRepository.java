package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.attribute.AttributeDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, UUID> {

    Optional<AttributeDefinition> findByNameAndActiveTrue(String name);

    boolean existsByNameAndActiveTrue(String name);

    boolean existsByNameAndActiveTrueAndIdNot(String name, UUID id);

    Page<AttributeDefinition> findAllByActiveTrue(Pageable pageable);
}
