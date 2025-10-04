package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.attribute.AttributeStatus;
import com.stockshift.backend.domain.attribute.AttributeValue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttributeValueRepository extends JpaRepository<AttributeValue, UUID> {

    Page<AttributeValue> findAllByDefinitionId(UUID definitionId, Pageable pageable);
    
    Page<AttributeValue> findAllByDefinitionIdAndStatus(UUID definitionId, AttributeStatus status, Pageable pageable);
    
    Optional<AttributeValue> findByDefinitionIdAndCode(UUID definitionId, String code);
    
    boolean existsByDefinitionIdAndCode(UUID definitionId, String code);
}
