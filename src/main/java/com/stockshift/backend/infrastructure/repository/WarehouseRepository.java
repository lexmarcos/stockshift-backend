package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.warehouse.Warehouse;
import com.stockshift.backend.domain.warehouse.WarehouseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    Optional<Warehouse> findByCode(String code);

    Optional<Warehouse> findByCodeAndActiveTrue(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    Page<Warehouse> findAllByActiveTrue(Pageable pageable);

    Page<Warehouse> findAllByType(WarehouseType type, Pageable pageable);

    Page<Warehouse> findAllByTypeAndActiveTrue(WarehouseType type, Pageable pageable);

    Page<Warehouse> findAllByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code, Pageable pageable);
}
