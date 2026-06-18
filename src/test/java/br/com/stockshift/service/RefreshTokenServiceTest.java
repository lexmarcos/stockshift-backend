package br.com.stockshift.service;

import br.com.stockshift.config.JwtProperties;
import br.com.stockshift.exception.UnauthorizedException;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final long GRACE_MS = 60_000L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setRefreshExpiration(604_800_000L);
        jwtProperties.setRefreshRotationGracePeriod(GRACE_MS);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, jwtProperties);

        user = new User();
        user.setId(UUID.randomUUID());
    }

    @Test
    void validateRefreshTokenAcceptsRotatedTokenWithinGraceWindow() {
        RefreshToken rotated = activeToken();
        rotated.setRotatedAt(LocalDateTime.now().minusSeconds(5));
        when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.of(rotated));

        assertThat(refreshTokenService.validateRefreshToken("rt")).isSameAs(rotated);
    }

    @Test
    void validateRefreshTokenRejectsRotatedTokenAfterGraceWindow() {
        RefreshToken rotated = activeToken();
        rotated.setRotatedAt(LocalDateTime.now().minusSeconds(120));
        when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.of(rotated));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("rt"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("already rotated");
    }

    @Test
    void validateRefreshTokenRejectsExpiredToken() {
        RefreshToken expired = activeToken();
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("rt"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");
    }

    // Regression: rotation must NOT wipe the user's tokens. The old createRefreshToken
    // path called deleteByUser, which deleted the token a concurrent refresh had just
    // issued, forcing a logout right after a successful login (see audit trail bug).
    @Test
    void rotateRefreshTokenStampsCurrentAndIssuesNewTokenWithoutDeletingAll() {
        when(refreshTokenRepository.save(any())).then(returnsFirstArg());
        RefreshToken current = activeToken();
        UUID warehouseId = UUID.randomUUID();

        RefreshToken issued = refreshTokenService.rotateRefreshToken(current, warehouseId);

        verify(refreshTokenRepository, never()).deleteByUser(any());
        assertThat(current.getRotatedAt()).isNotNull();
        assertThat(issued.getToken()).isNotEqualTo(current.getToken());
        assertThat(issued.getRotatedAt()).isNull();
        assertThat(issued.getWarehouseId()).isEqualTo(warehouseId);
    }

    // Regression: two concurrent refreshes carrying the same original cookie must both
    // succeed, and the original must remain valid throughout its grace window instead
    // of being invalidated by the first rotation.
    @Test
    void concurrentRefreshesOfSameTokenBothSucceedAndKeepOriginalValid() {
        when(refreshTokenRepository.save(any())).then(returnsFirstArg());
        RefreshToken original = activeToken();
        when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.of(original));
        UUID warehouseId = UUID.randomUUID();

        RefreshToken validatedA = refreshTokenService.validateRefreshToken("rt");
        RefreshToken issuedA = refreshTokenService.rotateRefreshToken(validatedA, warehouseId);
        LocalDateTime rotatedAtAfterFirst = original.getRotatedAt();

        // Second request still holds the original token (cookie not yet updated).
        RefreshToken validatedB = refreshTokenService.validateRefreshToken("rt");
        RefreshToken issuedB = refreshTokenService.rotateRefreshToken(validatedB, warehouseId);

        assertThat(validatedB).isSameAs(original);
        assertThat(issuedA.getToken()).isNotEqualTo(issuedB.getToken());
        // Grace stamp is set once, so repeated refresh can't extend it indefinitely.
        assertThat(original.getRotatedAt()).isEqualTo(rotatedAtAfterFirst);
        verify(refreshTokenRepository, never()).deleteByUser(any());
    }

    // Abandoned siblings (unrotated tokens left over from a concurrent refresh)
    // must be purged on the next refresh instead of lingering for the full lifetime.
    @Test
    void rotateRefreshTokenPurgesAbandonedSiblingsOlderThanGrace() {
        when(refreshTokenRepository.save(any())).then(returnsFirstArg());
        RefreshToken current = activeToken();

        refreshTokenService.rotateRefreshToken(current, UUID.randomUUID());

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refreshTokenRepository)
                .deleteAbandonedSiblings(eq(user), eq(current.getId()), cutoff.capture());
        assertThat(cutoff.getValue())
                .isBefore(LocalDateTime.now().minusSeconds((GRACE_MS / 1000) - 5))
                .isAfter(LocalDateTime.now().minusSeconds((GRACE_MS / 1000) + 5));
    }

    private RefreshToken activeToken() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        token.setCreatedAt(LocalDateTime.now());
        return token;
    }
}
