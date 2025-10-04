package com.stockshift.backend.infrastructure.repository;

import com.stockshift.backend.domain.category.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByNameAndActiveTrue(String name);

    boolean existsByNameAndActiveTrue(String name);

    boolean existsByNameAndActiveTrueAndIdNot(String name, UUID id);

    Page<Category> findAllByActiveTrue(Pageable pageable);

    @Query("SELECT c FROM Category c WHERE c.parent IS NULL AND c.active = true")
    Page<Category> findRootCategories(Pageable pageable);

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId AND c.active = true")
    Page<Category> findByParentId(@Param("parentId") UUID parentId, Pageable pageable);

    @Query("SELECT c FROM Category c WHERE c.path LIKE CONCAT(:path, '%') AND c.active = true")
    List<Category> findDescendants(@Param("path") String path);
}
