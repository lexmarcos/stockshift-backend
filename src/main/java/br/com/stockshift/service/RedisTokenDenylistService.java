package br.com.stockshift.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisTokenDenylistService implements TokenDenylistService {

    private static final String DENYLIST_KEY_PREFIX = "token:denylist:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addToDenylist(String jti, long ttlMillis) {
        try {
            String key = DENYLIST_KEY_PREFIX + jti;
            redisTemplate.opsForValue().set(key, "1", ttlMillis, TimeUnit.MILLISECONDS);
            log.debug("Added token to denylist: {}", jti);
        } catch (Exception e) {
            // Fail-open: log error but don't block logout
            log.error("Failed to add token to denylist: {}. Error: {}", jti, e.getMessage());
        }
    }

    @Override
    public boolean isDenylisted(String jti) {
        try {
            String key = DENYLIST_KEY_PREFIX + jti;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            // Fail-open: if Redis is unavailable, allow token (prioritize availability)
            log.warn("Failed to check token denylist: {}. Allowing token (fail-open). Error: {}", jti, e.getMessage());
            return false;
        }
    }
}
