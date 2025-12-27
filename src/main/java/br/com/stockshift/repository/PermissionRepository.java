package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Permission;
import br.com.stockshift.model.enums.PermissionAction;
import br.com.stockshift.model.enums.PermissionResource;
import br.com.stockshift.model.enums.PermissionScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByResourceAndActionAndScope(
        PermissionResource resource,
        PermissionAction action,
        PermissionScope scope
    );
}
