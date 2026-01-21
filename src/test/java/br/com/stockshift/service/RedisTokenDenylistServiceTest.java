package br.com.stockshift.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

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

    @BeforeEach
    void setUp() {
        denylistService = new RedisTokenDenylistService(redisTemplate);
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
    void isDenylisted_whenRedisThrowsException_shouldReturnFalse() {
        // Given (fail-open strategy)
        String jti = "any-jti";
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        // When
        boolean result = denylistService.isDenylisted(jti);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void addToDenylist_whenRedisThrowsException_shouldNotThrow() {
        // Given (fail-open strategy)
        String jti = "test-jti";
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis unavailable"));

        // When/Then - should not throw
        denylistService.addToDenylist(jti, 60000L);
    }
}
