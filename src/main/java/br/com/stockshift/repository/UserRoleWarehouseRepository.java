package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Role;
import br.com.stockshift.model.entity.UserRoleWarehouse;
import br.com.stockshift.model.entity.UserRoleWarehouseId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public interface UserRoleWarehouseRepository extends JpaRepository<UserRoleWarehouse, UserRoleWarehouseId> {

    @Query("SELECT COUNT(urw) > 0 FROM UserRoleWarehouse urw WHERE urw.user.id = :userId AND urw.warehouse.id = :warehouseId")
    boolean existsByUserIdAndWarehouseId(@Param("userId") UUID userId, @Param("warehouseId") UUID warehouseId);

    @Query("SELECT DISTINCT urw.warehouse.id FROM UserRoleWarehouse urw WHERE urw.user.id = :userId")
    Set<UUID> findWarehouseIdsByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT DISTINCT urw.role
            FROM UserRoleWarehouse urw
            LEFT JOIN FETCH urw.role.permissions
            WHERE urw.user.id = :userId
              AND urw.warehouse.id = :warehouseId
            """)
    Set<Role> findRolesByUserIdAndWarehouseId(@Param("userId") UUID userId, @Param("warehouseId") UUID warehouseId);

    @Modifying
    @Query("DELETE FROM UserRoleWarehouse urw WHERE urw.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
