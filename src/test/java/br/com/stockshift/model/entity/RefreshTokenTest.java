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
}
