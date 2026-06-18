package br.com.stockshift.service;

import br.com.stockshift.config.JwtProperties;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Rotates a refresh token without destroying tokens issued by concurrent
     * refreshes. The current token is marked rotated (kept valid until its grace
     * window closes) and a brand-new token is issued. This is what prevents the
     * concurrent-refresh logout: two requests sharing the same cookie both
     * succeed instead of one deleting the other's token.
     */
    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken current, UUID warehouseId) {
        User user = current.getUser();
        cleanupAbandonedSiblings(user, current.getId());
        cleanupStaleTokens(user);
        markRotated(current);
        return refreshTokenRepository.save(buildToken(user, warehouseId));
    }

    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.isExpired()) {
            throw new UnauthorizedException("Refresh token expired");
        }

        if (refreshToken.isRotationGraceExpired(graceSeconds())) {
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

    // Stamp rotation only once so repeated refreshes can't extend the grace window
    // indefinitely (which would defeat rotation as a token-theft mitigation).
    private void markRotated(RefreshToken token) {
        if (token.getRotatedAt() == null) {
            token.setRotatedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        }
    }

    private void cleanupStaleTokens(User user) {
        LocalDateTime graceCutoff = LocalDateTime.now().minusSeconds(graceSeconds());
        refreshTokenRepository.deleteRotatedBefore(user, graceCutoff);
    }

    // The presented token is the proven-active one, so any other still-unrotated
    // token older than the grace window is a leftover from a concurrent refresh
    // (its in-flight Set-Cookie has long landed) and would otherwise stay valid
    // until the 7-day refresh expiry.
    private void cleanupAbandonedSiblings(User user, UUID currentTokenId) {
        LocalDateTime graceCutoff = LocalDateTime.now().minusSeconds(graceSeconds());
        refreshTokenRepository.deleteAbandonedSiblings(user, currentTokenId, graceCutoff);
    }

    private long graceSeconds() {
        return jwtProperties.getRefreshRotationGracePeriod() / 1000;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findOwnerId(String token) {
        return refreshTokenRepository.findByToken(token).map(rt -> rt.getUser().getId());
    }

    // Revokes the whole session on logout: every token of the user, not just the
    // presented one, so concurrent-refresh siblings can't outlive the logout and
    // silently restore the session.
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
