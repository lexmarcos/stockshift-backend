package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.brand.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BrandRepository extends JpaRepository<Brand, UUID> {
    Optional<Brand> findByName(String name);
    boolean existsByName(String name);
    Page<Brand> findByActiveTrue(Pageable pageable);
}
