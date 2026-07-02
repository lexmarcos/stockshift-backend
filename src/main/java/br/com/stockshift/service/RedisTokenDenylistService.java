package br.com.stockshift.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@Service
@Slf4j
public class RedisTokenDenylistService implements TokenDenylistService {

    private static final String DENYLIST_KEY_PREFIX = "token:denylist:";

    private final StringRedisTemplate redisTemplate;
    private final ConcurrentMap<String, Long> fallbackDenylist;
    private final LongSupplier currentTimeMillis;

    @Autowired
    public RedisTokenDenylistService(StringRedisTemplate redisTemplate) {
        this(redisTemplate, System::currentTimeMillis);
    }

    RedisTokenDenylistService(StringRedisTemplate redisTemplate, LongSupplier currentTimeMillis) {
        this.redisTemplate = redisTemplate;
        this.currentTimeMillis = currentTimeMillis;
        this.fallbackDenylist = new ConcurrentHashMap<>();
    }

    @Override
    public void addToDenylist(String jti, long ttlMillis) {
        fallbackDenylist.put(jti, expiresAtMillis(ttlMillis));
        try {
            storeInRedis(jti, ttlMillis);
            log.debug("Added token to denylist: {}", jti);
        } catch (Exception e) {
            log.error("Failed to add token to Redis denylist: {}. Token remains denylisted locally. Error: {}",
                    jti, e.getMessage());
        }
    }

    @Override
    public boolean isDenylisted(String jti) {
        if (isLocallyDenylisted(jti)) {
            return true;
        }
        try {
            String key = DENYLIST_KEY_PREFIX + jti;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Failed to check token denylist: {}. Denying token (fail-closed). Error: {}",
                    jti, e.getMessage());
            return true;
        }
    }

    private void storeInRedis(String jti, long ttlMillis) {
        String key = DENYLIST_KEY_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "1", ttlMillis, TimeUnit.MILLISECONDS);
    }

    private boolean isLocallyDenylisted(String jti) {
        Long expiresAt = fallbackDenylist.get(jti);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt > currentTimeMillis.getAsLong()) {
            return true;
        }
        fallbackDenylist.remove(jti, expiresAt);
        return false;
    }

    private long expiresAtMillis(long ttlMillis) {
        long now = currentTimeMillis.getAsLong();
        long safeTtlMillis = Math.max(ttlMillis, 0L);
        if (safeTtlMillis >= Long.MAX_VALUE - now) {
            return Long.MAX_VALUE;
        }
        return now + safeTtlMillis;
    }
}
