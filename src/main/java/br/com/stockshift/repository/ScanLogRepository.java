package br.com.stockshift.repository;

import br.com.stockshift.model.entity.ScanLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanLogRepository extends JpaRepository<ScanLog, UUID> {

    Optional<ScanLog> findByIdempotencyKey(UUID idempotencyKey);

    @Modifying
    @Query("DELETE FROM ScanLog s WHERE s.expiresAt < :now")
    void deleteExpiredBefore(LocalDateTime now);
}
