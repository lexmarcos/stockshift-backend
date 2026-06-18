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
        markRotated(current);
        cleanupStaleTokens(user);
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

    private long graceSeconds() {
        return jwtProperties.getRefreshRotationGracePeriod() / 1000;
    }

    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token)
                .ifPresent(refreshTokenRepository::delete);
    }

    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.deleteByUser(user);
    }
}
