package br.com.stockshift.model.entity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    @Test
    void isExpiredShouldCompareExpirationWithCurrentTime() {
        RefreshToken expired = new RefreshToken();
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));

        RefreshToken active = new RefreshToken();
        active.setExpiresAt(LocalDateTime.now().plusSeconds(60));

        assertThat(expired.isExpired()).isTrue();
        assertThat(active.isExpired()).isFalse();
    }

    @Test
    void freshTokenIsNotRotatedAndNeverGraceExpired() {
        RefreshToken fresh = new RefreshToken();

        assertThat(fresh.isRotated()).isFalse();
        assertThat(fresh.isRotationGraceExpired(Duration.ofSeconds(60))).isFalse();
    }

    @Test
    void rotatedTokenStaysUsableWithinGraceWindow() {
        RefreshToken token = new RefreshToken();
        token.setRotatedAt(LocalDateTime.now().minusSeconds(5));

        assertThat(token.isRotated()).isTrue();
        assertThat(token.isRotationGraceExpired(Duration.ofSeconds(60))).isFalse();
    }

    @Test
    void rotatedTokenIsRejectedAfterGraceWindow() {
        RefreshToken token = new RefreshToken();
        token.setRotatedAt(LocalDateTime.now().minusSeconds(120));

        assertThat(token.isRotationGraceExpired(Duration.ofSeconds(60))).isTrue();
    }

    // A sub-second grace window must keep its precision: a token rotated 100ms ago is
    // still inside an 800ms window. Truncating to whole seconds would collapse it to 0
    // and wrongly report it expired.
    @Test
    void subSecondGraceWindowIsHonored() {
        RefreshToken token = new RefreshToken();
        token.setRotatedAt(LocalDateTime.now().minus(Duration.ofMillis(100)));

        assertThat(token.isRotationGraceExpired(Duration.ofMillis(800))).isFalse();
    }
}
