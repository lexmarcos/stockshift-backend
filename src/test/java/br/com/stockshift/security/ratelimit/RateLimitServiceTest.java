package br.com.stockshift.security.ratelimit;

import br.com.stockshift.config.RateLimitProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {RateLimitService.class, RateLimitProperties.class})
@Testcontainers
class RateLimitServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // Rate limit config for testing (low values)
        registry.add("rate-limit.login.capacity", () -> 3);
        registry.add("rate-limit.login.refill-tokens", () -> 3);
        registry.add("rate-limit.login.refill-duration-minutes", () -> 1);
    }

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private RateLimitProperties properties;

    @Test
    @DisplayName("Should allow requests within limit")
    void shouldAllowRequestsWithinLimit() {
        String ip = "192.168.1.100";

        for (int i = 0; i < properties.getCapacity(); i++) {
            assertThat(rateLimitService.tryConsume(ip))
                    .as("Attempt %d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should block requests over limit")
    void shouldBlockRequestsOverLimit() {
        String ip = "192.168.1.101";

        // Consume all tokens
        for (int i = 0; i < properties.getCapacity(); i++) {
            rateLimitService.tryConsume(ip);
        }

        // Next attempt should be blocked
        assertThat(rateLimitService.tryConsume(ip))
                .as("Request over limit should be blocked")
                .isFalse();
    }

    @Test
    @DisplayName("Should track different IPs separately")
    void shouldTrackDifferentIpsSeparately() {
        String ip1 = "192.168.1.102";
        String ip2 = "192.168.1.103";

        // Consume all tokens for IP1
        for (int i = 0; i < properties.getCapacity(); i++) {
            rateLimitService.tryConsume(ip1);
        }

        // IP1 should be blocked
        assertThat(rateLimitService.tryConsume(ip1)).isFalse();

        // IP2 should still be allowed
        assertThat(rateLimitService.tryConsume(ip2)).isTrue();
    }

    @Test
    @DisplayName("Should return correct retry-after seconds")
    void shouldReturnCorrectRetryAfterSeconds() {
        long retryAfter = rateLimitService.getRetryAfterSeconds();

        assertThat(retryAfter)
                .isEqualTo(properties.getRefillDurationMinutes() * 60L);
    }
}
