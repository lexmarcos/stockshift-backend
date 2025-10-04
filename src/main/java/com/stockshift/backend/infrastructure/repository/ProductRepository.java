package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByNameAndActiveTrue(String name);

    boolean existsByNameAndActiveTrue(String name);

    boolean existsByNameAndActiveTrueAndIdNot(String name, UUID id);

    Page<Product> findAllByActiveTrue(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.brand.id = :brandId AND p.active = true")
    Page<Product> findByBrandId(@Param("brandId") UUID brandId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.active = true")
    Page<Product> findByCategoryId(@Param("categoryId") UUID categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.expiryDate IS NOT NULL AND p.expiryDate < :date AND p.active = true")
    Page<Product> findExpiredProducts(@Param("date") LocalDate date, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.expiryDate IS NOT NULL AND p.expiryDate BETWEEN :startDate AND :endDate AND p.active = true")
    Page<Product> findProductsExpiringBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) AND p.active = true")
    Page<Product> searchByName(@Param("search") String search, Pageable pageable);
}
