package br.com.stockshift.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisTokenDenylistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisTokenDenylistService denylistService;
    private AtomicLong nowMillis;

    @BeforeEach
    void setUp() {
        nowMillis = new AtomicLong(1000L);
        denylistService = new RedisTokenDenylistService(redisTemplate, nowMillis::get);
    }

    @Test
    void addToDenylist_shouldStoreJtiWithTtl() {
        // Given
        String jti = "test-jti-123";
        long ttlMillis = 60000L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        denylistService.addToDenylist(jti, ttlMillis);

        // Then
        verify(valueOperations).set(
                eq("token:denylist:test-jti-123"),
                eq("1"),
                eq(60000L),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void isDenylisted_whenJtiExists_shouldReturnTrue() {
        // Given
        String jti = "revoked-jti";
        when(redisTemplate.hasKey("token:denylist:revoked-jti")).thenReturn(true);

        // When
        boolean result = denylistService.isDenylisted(jti);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isDenylisted_whenJtiDoesNotExist_shouldReturnFalse() {
        // Given
        String jti = "valid-jti";
        when(redisTemplate.hasKey("token:denylist:valid-jti")).thenReturn(false);

        // When
        boolean result = denylistService.isDenylisted(jti);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isDenylisted_whenRedisThrowsException_shouldReturnTrue() {
        // Given
        String jti = "any-jti";
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        // When
        boolean result = denylistService.isDenylisted(jti);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void addToDenylist_whenRedisThrowsException_shouldDenylistLocally() {
        // Given
        String jti = "test-jti";
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis unavailable"));

        // When
        denylistService.addToDenylist(jti, 60000L);

        // Then
        assertThat(denylistService.isDenylisted(jti)).isTrue();
        verify(redisTemplate, never()).hasKey("token:denylist:test-jti");
    }

    @Test
    void isDenylisted_whenLocalFallbackEntryExpired_shouldCheckRedis() {
        // Given
        String jti = "expired-jti";
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis unavailable"));
        denylistService.addToDenylist(jti, 5L);
        nowMillis.addAndGet(6L);
        when(redisTemplate.hasKey("token:denylist:expired-jti")).thenReturn(false);

        // When
        boolean result = denylistService.isDenylisted(jti);

        // Then
        assertThat(result).isFalse();
    }
}
