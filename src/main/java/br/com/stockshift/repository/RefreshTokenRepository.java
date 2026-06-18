package br.com.stockshift.repository;

import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
    void deleteByUser(User user);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
    void deleteByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteExpiredTokens(LocalDateTime now);

    // Removes a user's tokens whose rotation grace window already closed, keeping
    // the table bounded without touching active or still-in-grace tokens.
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user AND rt.rotatedAt IS NOT NULL AND rt.rotatedAt < :cutoff")
    void deleteRotatedBefore(User user, LocalDateTime cutoff);

    // Removes unrotated tokens (other than the one being refreshed) created before
    // the grace cutoff: leftovers from concurrent refreshes that no client kept.
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user AND rt.rotatedAt IS NULL "
            + "AND rt.id <> :keepId AND rt.createdAt < :cutoff")
    void deleteAbandonedSiblings(User user, UUID keepId, LocalDateTime cutoff);
}
