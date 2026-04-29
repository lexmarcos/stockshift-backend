package br.com.stockshift.security;

import br.com.stockshift.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    @Test
    void shouldGenerateAndValidateTokenWithValidSecret() {
        JwtTokenProvider provider = tokenProvider("dev-secret-key-change-in-production-must-be-at-least-256-bits-long");
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = provider.generateAccessToken(
                userId, tenantId, "admin@stockshift.com", List.of("ADMIN"), List.of("PRODUCT_READ"));

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUserIdFromToken(token)).isEqualTo(userId);
        assertThat(provider.getTenantIdFromToken(token)).isEqualTo(tenantId);
    }

    @Test
    void shouldRejectUnresolvedJwtSecretPlaceholder() {
        JwtTokenProvider provider = tokenProvider("${JWT_SECRET}");

        assertThatThrownBy(provider::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("offending value: ${JWT_SECRET}")
                .hasMessageContaining("expected shape: at least 32 UTF-8 bytes");
    }

    @Test
    void shouldRejectShortJwtSecretWithoutLeakingValue() {
        JwtTokenProvider provider = tokenProvider("short-secret");

        assertThatThrownBy(provider::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("offending value: <redacted:12 bytes>")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("short-secret"));
    }

    private JwtTokenProvider tokenProvider(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setAccessExpiration(900_000L);
        properties.setRefreshExpiration(604_800_000L);
        return new JwtTokenProvider(properties);
    }
}
