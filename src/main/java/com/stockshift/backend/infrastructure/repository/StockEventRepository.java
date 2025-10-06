package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.stock.StockEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface StockEventRepository extends JpaRepository<StockEvent, UUID>, JpaSpecificationExecutor<StockEvent> {

    @EntityGraph(attributePaths = {"warehouse", "createdBy", "lines", "lines.variant", "lines.variant.product"})
    Optional<StockEvent> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"warehouse", "createdBy", "lines", "lines.variant", "lines.variant.product"})
    Optional<StockEvent> findByIdempotencyKey(String idempotencyKey);
}
