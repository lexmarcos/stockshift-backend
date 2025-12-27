package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByDocument(String document);
    Optional<Tenant> findByEmail(String email);
}
