package br.com.stockshift.security.ratelimit;

import br.com.stockshift.dto.auth.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RateLimitIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("stockshift_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // Rate limit with low threshold for testing
        registry.add("rate-limit.login.capacity", () -> 3);
        registry.add("rate-limit.login.refill-tokens", () -> 3);
        registry.add("rate-limit.login.refill-duration-minutes", () -> 15);
    }

    @Value("${local.server.port}")
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestClient createRestClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("Should return 429 when rate limit exceeded")
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        RestClient restClient = createRestClient();
        LoginRequest request = new LoginRequest("ratelimit-test@test.com", "wrongpassword");
        String jsonRequest = objectMapper.writeValueAsString(request);

        // Make requests up to the limit (3 attempts) - expect 401 Unauthorized
        for (int i = 0; i < 3; i++) {
            try {
                restClient.post()
                        .uri("/stockshift/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "10.0.0.200")
                        .body(jsonRequest)
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpClientErrorException.Unauthorized e) {
                // Expected - wrong credentials
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            }
        }

        // 4th attempt should be rate limited
        assertThatThrownBy(() -> {
            restClient.post()
                    .uri("/stockshift/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "10.0.0.200")
                    .body(jsonRequest)
                    .retrieve()
                    .toBodilessEntity();
        }).isInstanceOf(HttpClientErrorException.TooManyRequests.class)
          .satisfies(ex -> {
              HttpClientErrorException httpEx = (HttpClientErrorException) ex;
              assertThat(httpEx.getResponseHeaders().get("Retry-After")).isNotNull();
              assertThat(httpEx.getResponseBodyAsString()).contains("Muitas tentativas");
          });
    }

    @Test
    @DisplayName("Should allow requests from different IPs")
    void shouldAllowRequestsFromDifferentIps() throws Exception {
        RestClient restClient = createRestClient();
        LoginRequest request = new LoginRequest("ratelimit-test2@test.com", "wrongpassword");
        String jsonRequest = objectMapper.writeValueAsString(request);

        // Exhaust limit for IP1
        for (int i = 0; i < 3; i++) {
            try {
                restClient.post()
                        .uri("/stockshift/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "10.0.0.201")
                        .body(jsonRequest)
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpClientErrorException.Unauthorized e) {
                // Expected
            }
        }

        // IP1 should be blocked
        assertThatThrownBy(() -> {
            restClient.post()
                    .uri("/stockshift/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "10.0.0.201")
                    .body(jsonRequest)
                    .retrieve()
                    .toBodilessEntity();
        }).isInstanceOf(HttpClientErrorException.TooManyRequests.class);

        // IP2 should still work (returns 401 for wrong credentials, not 429)
        assertThatThrownBy(() -> {
            restClient.post()
                    .uri("/stockshift/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "10.0.0.202")
                    .body(jsonRequest)
                    .retrieve()
                    .toBodilessEntity();
        }).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }
}
