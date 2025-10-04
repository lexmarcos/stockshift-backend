package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.attribute.AttributeStatus;
import com.stockshift.backend.domain.attribute.AttributeValue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttributeValueRepository extends JpaRepository<AttributeValue, UUID> {

    // Legacy methods (kept for backward compatibility)
    @Query("SELECT av FROM AttributeValue av WHERE av.definition.id = :definitionId AND av.active = true")
    Page<AttributeValue> findByDefinitionIdAndActiveTrue(@Param("definitionId") UUID definitionId, Pageable pageable);

    @Query("SELECT av FROM AttributeValue av WHERE av.definition.id = :definitionId")
    Page<AttributeValue> findByDefinitionId(@Param("definitionId") UUID definitionId, Pageable pageable);

    @Query("SELECT av FROM AttributeValue av WHERE av.definition.id = :definitionId AND av.value = :value AND av.active = true")
    Optional<AttributeValue> findByDefinitionIdAndValueAndActiveTrue(@Param("definitionId") UUID definitionId, @Param("value") String value);

    @Query("SELECT COUNT(av) > 0 FROM AttributeValue av WHERE av.definition.id = :definitionId AND av.value = :value AND av.active = true")
    boolean existsByDefinitionIdAndValueAndActiveTrue(@Param("definitionId") UUID definitionId, @Param("value") String value);

    @Query("SELECT COUNT(av) > 0 FROM AttributeValue av WHERE av.definition.id = :definitionId AND av.value = :value AND av.active = true AND av.id != :id")
    boolean existsByDefinitionIdAndValueAndActiveTrueAndIdNot(@Param("definitionId") UUID definitionId, @Param("value") String value, @Param("id") UUID id);

    // New methods using code and status
    Page<AttributeValue> findAllByDefinitionId(UUID definitionId, Pageable pageable);
    Page<AttributeValue> findAllByDefinitionIdAndStatus(UUID definitionId, AttributeStatus status, Pageable pageable);
    Optional<AttributeValue> findByDefinitionIdAndCode(UUID definitionId, String code);
    boolean existsByDefinitionIdAndCode(UUID definitionId, String code);
}
