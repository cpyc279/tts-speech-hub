package com.tts.speech.deploy.common.redis;

import com.tts.speech.deploy.common.metrics.TtsMetricsCollector;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * RedisOperator 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-20 11:20:00
 */
class RedisOperatorTest {

    /**
     * 验证 Redis 异常时会返回降级值并记录指标。
     */
    @Test
    void testGetValueShouldReturnFallbackWhenRedisThrows() {
        RedisOperator redisOperator = createRedisOperator();
        ValueOperations<String, String> valueOperations = getValueOperations(redisOperator);
        TtsMetricsCollector ttsMetricsCollector = getTtsMetricsCollector(redisOperator);
        Mockito.when(valueOperations.get("cache-key")).thenThrow(new RuntimeException("redis unavailable"));

        String value = redisOperator.getValue("audio_cache_get", "cache-key", "fallback", "读取失败，key={}", "cache-key");

        Assertions.assertEquals("fallback", value);
        Mockito.verify(ttsMetricsCollector).recordRedisDegrade("audio_cache_get");
    }

    /**
     * 验证 Hash 读取成功时会直接返回原始结果。
     */
    @Test
    void testGetHashEntriesShouldReturnEntriesWhenRedisAvailable() {
        RedisOperator redisOperator = createRedisOperator();
        @SuppressWarnings("unchecked")
        HashOperations<String, String, String> hashOperations = getHashOperations(redisOperator);
        Mockito.when(hashOperations.entries("hash-key")).thenReturn(Map.of("field", "value"));

        Map<String, String> valueMap = redisOperator.getHashEntries(
            "inspect_sample_get",
            "hash-key",
            Map.of(),
            "读取失败，key={}",
            "hash-key");

        Assertions.assertEquals(Map.of("field", "value"), valueMap);
    }

    /**
     * 验证字符串写入成功时返回 true。
     */
    @Test
    void testSetValueShouldReturnTrueWhenRedisAvailable() {
        RedisOperator redisOperator = createRedisOperator();

        boolean success = redisOperator.setValue(
            "audio_cache_put",
            "cache-key",
            "cache-value",
            Duration.ofSeconds(30),
            "写入失败，key={}",
            "cache-key");

        Assertions.assertTrue(success);
    }

    /**
     * 创建 Redis 访问组件。
     *
     * @return Redis 访问组件
     */
    private RedisOperator createRedisOperator() {
        RedisTemplate<String, String> redisTemplate = Mockito.mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, String, String> hashOperations = Mockito.mock(HashOperations.class);
        TtsMetricsCollector ttsMetricsCollector = Mockito.mock(TtsMetricsCollector.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.doReturn(hashOperations).when(redisTemplate).opsForHash();
        return new RedisOperator(redisTemplate, ttsMetricsCollector);
    }

    /**
     * 获取字符串操作对象。
     *
     * @param redisOperator Redis 访问组件
     * @return 字符串操作对象
     */
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> getValueOperations(RedisOperator redisOperator) {
        RedisTemplate<String, String> redisTemplate =
            (RedisTemplate<String, String>) ReflectionTestUtils.getField(redisOperator, "redisTemplate");
        return redisTemplate.opsForValue();
    }

    /**
     * 获取 Hash 操作对象。
     *
     * @param redisOperator Redis 访问组件
     * @return Hash 操作对象
     */
    @SuppressWarnings("unchecked")
    private HashOperations<String, String, String> getHashOperations(RedisOperator redisOperator) {
        RedisTemplate<String, String> redisTemplate =
            (RedisTemplate<String, String>) ReflectionTestUtils.getField(redisOperator, "redisTemplate");
        return redisTemplate.opsForHash();
    }

    /**
     * 获取指标采集器。
     *
     * @param redisOperator Redis 访问组件
     * @return 指标采集器
     */
    private TtsMetricsCollector getTtsMetricsCollector(RedisOperator redisOperator) {
        return (TtsMetricsCollector) ReflectionTestUtils.getField(redisOperator, "ttsMetricsCollector");
    }
}
