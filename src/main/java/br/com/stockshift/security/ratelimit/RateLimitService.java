package br.com.stockshift.security.ratelimit;

import br.com.stockshift.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RateLimitProperties properties;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    private static final String KEY_PREFIX = "rate_limit:login:";

    private RedisClient redisClient;
    private StatefulRedisConnection<String, byte[]> connection;
    private ProxyManager<String> proxyManager;

    @PostConstruct
    public void init() {
        String redisUri = buildRedisUri();
        this.redisClient = RedisClient.create(redisUri);
        this.connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                        Duration.ofMinutes(properties.getRefillDurationMinutes() * 2)))
                .build();

        log.info("RateLimitService initialized with capacity={}, refillTokens={}, refillDuration={}min",
                properties.getCapacity(),
                properties.getRefillTokens(),
                properties.getRefillDurationMinutes());
    }

    @PreDestroy
    public void destroy() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    private String buildRedisUri() {
        if (redisPassword != null && !redisPassword.isEmpty()) {
            return String.format("redis://%s@%s:%d", redisPassword, redisHost, redisPort);
        }
        return String.format("redis://%s:%d", redisHost, redisPort);
    }

    /**
     * Attempts to consume one token from the bucket for the given client IP.
     *
     * @param clientIp the client's IP address
     * @return true if the request is allowed, false if rate limit exceeded
     */
    public boolean tryConsume(String clientIp) {
        String key = KEY_PREFIX + clientIp;

        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.getCapacity())
                        .refillGreedy(properties.getRefillTokens(),
                                Duration.ofMinutes(properties.getRefillDurationMinutes()))
                        .build())
                .build();

        return proxyManager.builder()
                .build(key, () -> configuration)
                .tryConsume(1);
    }

    /**
     * Gets the number of seconds until the rate limit resets.
     *
     * @return seconds until refill
     */
    public long getRetryAfterSeconds() {
        return properties.getRefillDurationMinutes() * 60L;
    }

    /**
     * Gets the number of remaining tokens for the given client IP.
     *
     * @param clientIp the client's IP address
     * @return number of remaining tokens
     */
    public long getRemainingTokens(String clientIp) {
        String key = KEY_PREFIX + clientIp;

        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.getCapacity())
                        .refillGreedy(properties.getRefillTokens(),
                                Duration.ofMinutes(properties.getRefillDurationMinutes()))
                        .build())
                .build();

        return proxyManager.builder()
                .build(key, () -> configuration)
                .getAvailableTokens();
    }

    /**
     * Determines if captcha should be required based on remaining login attempts.
     * Captcha is required when less than half of the capacity remains.
     *
     * @param clientIp the client's IP address
     * @return true if captcha should be required
     */
    public boolean shouldRequireCaptcha(String clientIp) {
        long remaining = getRemainingTokens(clientIp);
        int threshold = properties.getCapacity() / 2;
        return remaining <= threshold;
    }
}
