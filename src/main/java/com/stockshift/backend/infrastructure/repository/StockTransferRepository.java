package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.stock.StockTransfer;
import com.stockshift.backend.domain.stock.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    @EntityGraph(attributePaths = {
            "originWarehouse",
            "destinationWarehouse",
            "createdBy",
            "confirmedBy",
            "outboundEvent",
            "inboundEvent",
            "lines",
            "lines.variant"
    })
    Optional<StockTransfer> findWithDetailsById(UUID id);

    Optional<StockTransfer> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM StockTransfer t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:originWarehouseId IS NULL OR t.originWarehouse.id = :originWarehouseId) AND " +
           "(:destinationWarehouseId IS NULL OR t.destinationWarehouse.id = :destinationWarehouseId) AND " +
           "(:occurredFrom IS NULL OR t.occurredAt >= :occurredFrom) AND " +
           "(:occurredTo IS NULL OR t.occurredAt <= :occurredTo)")
    Page<StockTransfer> findByFilters(
            @Param("status") TransferStatus status,
            @Param("originWarehouseId") UUID originWarehouseId,
            @Param("destinationWarehouseId") UUID destinationWarehouseId,
            @Param("occurredFrom") OffsetDateTime occurredFrom,
            @Param("occurredTo") OffsetDateTime occurredTo,
            Pageable pageable
    );
}
