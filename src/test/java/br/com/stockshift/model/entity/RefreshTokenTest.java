package br.com.stockshift.model.entity;

import org.junit.jupiter.api.Test;

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
        assertThat(fresh.isRotationGraceExpired(60)).isFalse();
    }

    @Test
    void rotatedTokenStaysUsableWithinGraceWindow() {
        RefreshToken token = new RefreshToken();
        token.setRotatedAt(LocalDateTime.now().minusSeconds(5));

        assertThat(token.isRotated()).isTrue();
        assertThat(token.isRotationGraceExpired(60)).isFalse();
    }

    @Test
    void rotatedTokenIsRejectedAfterGraceWindow() {
        RefreshToken token = new RefreshToken();
        token.setRotatedAt(LocalDateTime.now().minusSeconds(120));

        assertThat(token.isRotationGraceExpired(60)).isTrue();
    }
}
