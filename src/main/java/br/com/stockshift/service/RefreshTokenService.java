package br.com.stockshift.service;

import br.com.stockshift.config.JwtProperties;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        return createRefreshToken(user, null);
    }

    @Transactional
    public RefreshToken createRefreshToken(User user, UUID warehouseId) {
        // Clean-slate creation (login / warehouse switch): drop every existing token.
        refreshTokenRepository.deleteByUser(user);
        return refreshTokenRepository.save(buildToken(user, warehouseId));
    }

    /**
     * Rotates a refresh token. Only an unrotated token mints a successor, and the
     * rotation is claimed atomically (see {@link RefreshTokenRepository#claimRotation})
     * so overlapping refreshes of the same token can't each mint a different one: the
     * loser discards its orphan and returns the winner's successor. A token already
     * rotated within its grace window (retry / replay of the pre-rotation cookie)
     * likewise returns its single tracked successor instead of a new long-lived token.
     * Example: refresh(rt) -> rt2; a second refresh(rt) within grace -> rt2.
     */
    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken current, UUID warehouseId) {
        if (current.getReplacedById() != null) {
            return findSuccessorOrThrow(current.getReplacedById());
        }

        User user = current.getUser();
        cleanupAbandonedSiblings(user, current.getId());
        cleanupStaleTokens(user);

        RefreshToken successor = refreshTokenRepository.save(buildToken(user, warehouseId));
        boolean claimed = refreshTokenRepository.claimRotation(
                current.getId(), LocalDateTime.now(), successor.getId()) == 1;
        return claimed ? successor : resolveConcurrentWinner(current, successor);
    }

    // Another refresh rotated this token first. Drop our orphan successor and hand
    // back the winner's tracked successor so both responses converge on one token.
    private RefreshToken resolveConcurrentWinner(RefreshToken current, RefreshToken orphan) {
        refreshTokenRepository.delete(orphan);
        UUID successorId = refreshTokenRepository.findReplacedById(current.getId())
                .orElseThrow(() -> new UnauthorizedException(
                        "Concurrent rotation left no successor for token " + current.getId()));
        return findSuccessorOrThrow(successorId);
    }

    private RefreshToken findSuccessorOrThrow(UUID successorId) {
        return refreshTokenRepository.findById(successorId)
                .orElseThrow(() -> new UnauthorizedException(
                        "Refresh token successor missing: " + successorId));
    }

    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.isExpired()) {
            throw new UnauthorizedException("Refresh token expired");
        }

        if (refreshToken.isRotationGraceExpired(grace())) {
            throw new UnauthorizedException("Refresh token already rotated");
        }

        return refreshToken;
    }

    private RefreshToken buildToken(User user, UUID warehouseId) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(user);
        refreshToken.setWarehouseId(warehouseId);
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpiration() / 1000));
        refreshToken.setCreatedAt(LocalDateTime.now());
        return refreshToken;
    }

    private void cleanupStaleTokens(User user) {
        refreshTokenRepository.deleteRotatedBefore(user, graceCutoff());
    }

    // The presented token is the proven-active one, so any other still-unrotated
    // token older than the grace window is a leftover from a concurrent refresh
    // (its in-flight Set-Cookie has long landed) and would otherwise stay valid
    // until the 7-day refresh expiry.
    private void cleanupAbandonedSiblings(User user, UUID currentTokenId) {
        refreshTokenRepository.deleteAbandonedSiblings(user, currentTokenId, graceCutoff());
    }

    // Kept in milliseconds (not truncated to whole seconds) so a sub-second grace
    // window keeps its precision instead of collapsing to zero.
    private Duration grace() {
        return Duration.ofMillis(jwtProperties.getRefreshRotationGracePeriod());
    }

    private LocalDateTime graceCutoff() {
        return LocalDateTime.now().minus(grace());
    }

    // Revokes the whole session on logout: every token of the user, not just the
    // presented one, so concurrent-refresh siblings can't outlive the logout and
    // silently restore the session.
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
