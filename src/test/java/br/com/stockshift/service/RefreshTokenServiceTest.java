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

    // Regression: rotation must NOT wipe the user's tokens (the old createRefreshToken
    // path called deleteByUser, forcing a logout right after login). An unrotated token
    // mints a successor and atomically claims the rotation, tracking the successor.
    @Test
    void rotateMintsSuccessorAndClaimsRotationWhenCurrentNotYetRotated() {
        stubSaveAssigningIds();
        RefreshToken current = activeToken();
        when(refreshTokenRepository.claimRotation(eq(current.getId()), any(LocalDateTime.class), any(UUID.class)))
                .thenReturn(1);
        UUID warehouseId = UUID.randomUUID();

        RefreshToken successor = refreshTokenService.rotateRefreshToken(current, warehouseId);

        verify(refreshTokenRepository, never()).deleteByUser(any());
        ArgumentCaptor<UUID> successorId = ArgumentCaptor.forClass(UUID.class);
        verify(refreshTokenRepository)
                .claimRotation(eq(current.getId()), any(LocalDateTime.class), successorId.capture());
        assertThat(successorId.getValue()).isEqualTo(successor.getId());
        assertThat(successor.getToken()).isNotEqualTo(current.getToken());
        assertThat(successor.getWarehouseId()).isEqualTo(warehouseId);
    }

    // Security + convergence (codex review): a token already rotated within its grace
    // window returns the SAME tracked successor instead of minting a fresh long-lived
    // token, so a replayed pre-rotation cookie can't bootstrap a new session.
    @Test
    void rotatedTokenReturnsTrackedSuccessorWithoutMinting() {
        RefreshToken current = activeToken();
        UUID successorId = UUID.randomUUID();
        current.setRotatedAt(LocalDateTime.now());
        current.setReplacedById(successorId);
        RefreshToken successor = activeToken();
        successor.setId(successorId);
        when(refreshTokenRepository.findById(successorId)).thenReturn(Optional.of(successor));

        RefreshToken result = refreshTokenService.rotateRefreshToken(current, UUID.randomUUID());

        assertThat(result).isSameAs(successor);
        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).claimRotation(any(), any(), any());
    }

    // Concurrent-rotation race (codex review): if another refresh wins the atomic claim
    // first, this one must discard its freshly minted orphan and return the winner's
    // successor instead of leaving an extra valid token behind.
    @Test
    void rotateDiscardsOrphanAndReturnsWinnerWhenConcurrentClaimLost() {
        stubSaveAssigningIds();
        RefreshToken current = activeToken();
        when(refreshTokenRepository.claimRotation(any(UUID.class), any(LocalDateTime.class), any(UUID.class)))
                .thenReturn(0);
        UUID winnerSuccessorId = UUID.randomUUID();
        RefreshToken winnerSuccessor = activeToken();
        winnerSuccessor.setId(winnerSuccessorId);
        when(refreshTokenRepository.findReplacedById(current.getId())).thenReturn(Optional.of(winnerSuccessorId));
        when(refreshTokenRepository.findById(winnerSuccessorId)).thenReturn(Optional.of(winnerSuccessor));

        RefreshToken result = refreshTokenService.rotateRefreshToken(current, UUID.randomUUID());

        assertThat(result).isSameAs(winnerSuccessor);
        verify(refreshTokenRepository).delete(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).deleteByUser(any());
    }

    // Abandoned siblings (unrotated leftovers from a pre-fix concurrent refresh)
    // must still be purged on the next refresh instead of lingering for the lifetime.
    @Test
    void rotateRefreshTokenPurgesAbandonedSiblingsOlderThanGrace() {
        when(refreshTokenRepository.save(any())).then(returnsFirstArg());
        when(refreshTokenRepository.claimRotation(any(), any(LocalDateTime.class), any())).thenReturn(1);
        RefreshToken current = activeToken();

        refreshTokenService.rotateRefreshToken(current, UUID.randomUUID());

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refreshTokenRepository)
                .deleteAbandonedSiblings(eq(user), eq(current.getId()), cutoff.capture());
        assertThat(cutoff.getValue())
                .isBefore(LocalDateTime.now().minusSeconds((GRACE_MS / 1000) - 5))
                .isAfter(LocalDateTime.now().minusSeconds((GRACE_MS / 1000) + 5));
    }

    // Mimics the DB assigning a primary key on insert, so replacedById tracking works.
    private void stubSaveAssigningIds() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
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
