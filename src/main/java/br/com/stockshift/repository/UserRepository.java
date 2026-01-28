package br.com.stockshift.repository;

import br.com.stockshift.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);
    Optional<User> findByEmail(String email);
    List<User> findAllByTenantId(UUID tenantId);
    Optional<User> findByTenantIdAndId(UUID tenantId, UUID id);
}
