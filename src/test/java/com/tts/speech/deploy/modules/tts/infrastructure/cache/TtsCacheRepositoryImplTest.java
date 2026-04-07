package com.tts.speech.deploy.modules.tts.infrastructure.cache;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tts.speech.deploy.common.metrics.TtsMetricsCollector;
import com.tts.speech.deploy.common.redis.RedisOperator;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsAudioCacheEntity;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.DataType;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * TtsCacheRepositoryImpl 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-31 00:00:00
 */
class TtsCacheRepositoryImplTest {

    @Test
    void testGetAudioCacheShouldRefreshIndexWhenCacheExists() {
        TtsCacheRepositoryImpl repository = createRepository(2);
        RedisOperator redisOperator = getRedisOperator(repository);
        RedisOperator audioCacheRedisOperator = getAudioCacheRedisOperator(repository);
        ListAppender<ILoggingEvent> listAppender = attachListAppender();
        Mockito.when(audioCacheRedisOperator.getValue(
                Mockito.eq("audio_cache_get"),
                Mockito.eq("cache-key"),
                Mockito.isNull(),
                Mockito.anyString(),
                Mockito.eq("broker-a"),
                Mockito.eq("cache-key")))
            .thenReturn("{\"base64Audio\":\"cached-base64\"}");
        Mockito.when(redisOperator.getHashValue(
                Mockito.eq("audio_cache_index_read"),
                Mockito.eq("tts:cache:base64:index:broker-a"),
                Mockito.eq("cache-key"),
                Mockito.isNull(),
                Mockito.anyString(),
                Mockito.eq("broker-a"),
                Mockito.eq("cache-key")))
            .thenReturn("{\"cacheKey\":\"cache-key\",\"createdAt\":1,\"lastAccessedAt\":2,\"expireAt\":3}");

        TtsAudioCacheEntity audioCacheEntity = repository.getAudioCache("broker-a", "cache-key");

        Assertions.assertNotNull(audioCacheEntity);
        Assertions.assertEquals("cached-base64", audioCacheEntity.getBase64Audio());
        Assertions.assertEquals(1L, audioCacheEntity.getCreatedAt());
        Mockito.verify(redisOperator).putHashValue(
            Mockito.eq("audio_cache_index_write"),
            Mockito.eq("tts:cache:base64:index:broker-a"),
            Mockito.eq("cache-key"),
            Mockito.argThat(indexValue -> indexValue.contains("\"createdAt\":1")),
            Mockito.anyString(),
            Mockito.eq("broker-a"),
            Mockito.eq("cache-key"));
        Assertions.assertTrue(containsLog(listAppender, "cache-key"));
        detachListAppender(listAppender);
    }

    @Test
    void testGetAudioCacheShouldDeleteIndexWhenValueMissing() {
        TtsCacheRepositoryImpl repository = createRepository(2);
        RedisOperator redisOperator = getRedisOperator(repository);
        RedisOperator audioCacheRedisOperator = getAudioCacheRedisOperator(repository);
        Mockito.when(audioCacheRedisOperator.getValue(
                Mockito.eq("audio_cache_get"),
                Mockito.eq("cache-key"),
                Mockito.isNull(),
                Mockito.anyString(),
                Mockito.eq("broker-a"),
                Mockito.eq("cache-key")))
            .thenReturn(null);

        TtsAudioCacheEntity audioCacheEntity = repository.getAudioCache("broker-a", "cache-key");

        Assertions.assertNull(audioCacheEntity);
        Mockito.verify(redisOperator).deleteHashFields(
            Mockito.eq("audio_cache_index_delete"),
            Mockito.eq("tts:cache:base64:index:broker-a"),
            Mockito.argThat(hashKeys -> Arrays.equals((Object[]) hashKeys, new Object[] {"cache-key"})),
            Mockito.eq(0L),
            Mockito.anyString(),
            Mockito.eq("broker-a"),
            Mockito.eq(List.of("cache-key")));
    }

    @Test
    void testGetAudioUrlCacheShouldReadStringValue() {
        TtsCacheRepositoryImpl repository = createRepository(2);
        RedisOperator redisOperator = getRedisOperator(repository);
        Mockito.when(redisOperator.getValue(
                Mockito.eq("audio_url_cache_get"),
                Mockito.eq("tts:cache:url:broker-a:hithink:test"),
                Mockito.isNull(),
                Mockito.anyString(),
                Mockito.eq("broker-a"),
                Mockito.eq("tts:cache:url:broker-a:hithink:test")))
            .thenReturn("https://oss.example.com/audio.wav");

        String audioUrl = repository.getAudioUrlCache("broker-a", "tts:cache:url:broker-a:hithink:test");

        Assertions.assertEquals("https://oss.example.com/audio.wav", audioUrl);
    }

    @Test
    void testPutAudioCacheShouldRemoveExpiredEntryBeforeWrite() {
        TtsCacheRepositoryImpl repository = createRepository(3);
        RedisOperator redisOperator = getRedisOperator(repository);
        RedisOperator audioCacheRedisOperator = getAudioCacheRedisOperator(repository);
        TtsMetricsCollector metricsCollector = getMetricsCollector(repository);
        Mockito.when(redisOperator.getHashEntries(
                Mockito.eq("audio_cache_index_read"),
                Mockito.eq("tts:cache:base64:index:broker-a"),
                Mockito.anyMap(),
                Mockito.anyString(),
                Mockito.eq("broker-a"),
                Mockito.eq("tts:cache:base64:index:broker-a")))
            .thenReturn(Map.of(
                "cache-expired",
                "{\"cacheKey\":\"cache-expired\",\"createdAt\":1,\"lastAccessedAt\":1,\"expireAt\":1}"));

        repository.putAudioCache(
            "broker-a",
            "cache-new",
            TtsAudioCacheEntity.builder().base64Audio("base64").build());

        Mockito.verify(audioCacheRedisOperator).deleteKeys(
            Mockito.eq("cache_delete"),
            Mockito.eq(List.of("cache-expired")),
            Mockito.eq(0L),
            Mockito.anyString(),
            Mockito.eq("broker-a"),
            Mockito.eq(List.of("cache-expired")));
        Mockito.verify(metricsCollector).recordAudioCacheExpiredCleanup("broker-a");
        Mockito.verify(audioCacheRedisOperator).setValue(
            Mockito.eq("audio_cache_put"),
            Mockito.eq("cache-new"),
            Mockito.argThat(cacheValue -> cacheValue.contains("\"base64Audio\":\"base64\"")),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.eq("broker-a"),
            Mockito.eq("cache-new"));
    }

    @Test
    void testPutAudioUrlCacheShouldWriteStringValue() {
        TtsCacheRepositoryImpl repository = createRepository(2);
        RedisOperator redisOperator = getRedisOperator(repository);

        repository.putAudioUrlCache("broker-a", "tts:cache:url:broker-a:hithink:test", "https://oss.example.com/audio.wav");

        Mockito.verify(redisOperator).setValue(
            Mockito.eq("audio_url_cache_put"),
            Mockito.eq("tts:cache:url:broker-a:hithink:test"),
            Mockito.eq("https://oss.example.com/audio.wav"),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.eq("broker-a"),
            Mockito.eq("tts:cache:url:broker-a:hithink:test"));
    }

    @Test
    void testPutAudioCacheShouldEvictLeastRecentlyUsedEntryWhenCountExceedsLimit() {
        TtsCacheRepositoryImpl repository = createRepository(2);
        RedisOperator redisOperator = getRedisOperator(repository);
        RedisOperator audioCacheRedisOperator = getAudioCacheRedisOperator(repository);
        TtsMetricsCollector metricsCollector = getMetricsCollector(repository);
        ListAppender<ILoggingEvent> listAppender = attachListAppender();
        Mockito.when(redisOperator.getHashEntries(
                Mockito.eq("audio_cache_index_read"),
                Mockito.eq("tts:cache:base64:index:broker-a"),
                Mockito.anyMap(),
                Mockito.anyString(),
                Mockito.eq("broker-a"),
                Mockito.eq("tts:cache:base64:index:broker-a")))
            .thenReturn(Map.of(
                "tts:cache:base64:broker-a:hithink:old",
                "{\"cacheKey\":\"tts:cache:base64:broker-a:hithink:old\",\"createdAt\":1,\"lastAccessedAt\":10,\"expireAt\":9999999999999}",
                "tts:cache:base64:broker-a:hithink:newer",
                "{\"cacheKey\":\"tts:cache:base64:broker-a:hithink:newer\",\"createdAt\":1,\"lastAccessedAt\":20,\"expireAt\":9999999999999}"));
        Mockito.when(audioCacheRedisOperator.type(
                Mockito.eq("audio_cache_get"),
                Mockito.anyString(),
                Mockito.isNull(),
                Mockito.anyString(),
                Mockito.eq("broker-a"),
                Mockito.anyString()))
            .thenReturn(DataType.STRING);

        repository.putAudioCache(
            "broker-a",
            "tts:cache:base64:broker-a:hithink:latest",
            TtsAudioCacheEntity.builder().base64Audio("base64").build());

        Mockito.verify(audioCacheRedisOperator).deleteKeys(
            Mockito.eq("cache_delete"),
            Mockito.eq(List.of("tts:cache:base64:broker-a:hithink:old")),
            Mockito.eq(0L),
            Mockito.anyString(),
            Mockito.eq("broker-a"),
            Mockito.eq(List.of("tts:cache:base64:broker-a:hithink:old")));
        Mockito.verify(redisOperator).deleteKeys(
            Mockito.eq("cache_delete"),
            Mockito.eq(List.of("tts:cache:url:broker-a:hithink:old")),
            Mockito.eq(0L),
            Mockito.anyString(),
            Mockito.eq("broker-a"),
            Mockito.eq(List.of("tts:cache:url:broker-a:hithink:old")));
        Mockito.verify(metricsCollector).recordAudioCacheCapacityEviction("broker-a");
        Assertions.assertTrue(containsLog(listAppender, "broker-a"));
        Assertions.assertTrue(containsLog(listAppender, "tts:cache:base64:broker-a:hithink:old"));
        Assertions.assertTrue(containsLog(listAppender, "tts:cache:base64:broker-a:hithink:latest"));
        detachListAppender(listAppender);
    }

    /**
     * 创建仓储。
     *
     * @param maxCountPerBroker 单券商缓存上限
     * @return 仓储
     */
    private TtsCacheRepositoryImpl createRepository(int maxCountPerBroker) {
        TtsCacheRepositoryImpl repository = new TtsCacheRepositoryImpl();
        ReflectionTestUtils.setField(repository, "redisOperator", Mockito.mock(RedisOperator.class));
        ReflectionTestUtils.setField(repository, "audioCacheRedisOperator", Mockito.mock(RedisOperator.class));
        ReflectionTestUtils.setField(repository, "speechTtsProperties", buildSpeechTtsProperties(maxCountPerBroker));
        ReflectionTestUtils.setField(repository, "ttsMetricsCollector", Mockito.mock(TtsMetricsCollector.class));
        return repository;
    }

    /**
     * 构建配置。
     *
     * @param maxCountPerBroker 单券商缓存上限
     * @return 配置
     */
    private SpeechTtsProperties buildSpeechTtsProperties(int maxCountPerBroker) {
        SpeechTtsProperties speechTtsProperties = new SpeechTtsProperties();
        SpeechTtsProperties.CacheConfig cacheConfig = new SpeechTtsProperties.CacheConfig();
        cacheConfig.setBase64TtlSeconds(604800L);
        cacheConfig.setBase64KeyPrefix("tts:cache:base64");
        cacheConfig.setBase64IndexKeyPrefix("tts:cache:base64:index");
        cacheConfig.setBase64MaxCountPerBroker(maxCountPerBroker);
        cacheConfig.setUrlKeyPrefix("tts:cache:url");
        speechTtsProperties.setCache(cacheConfig);
        return speechTtsProperties;
    }

    /**
     * 获取 Redis 组件。
     *
     * @param repository 仓储
     * @return Redis 组件
     */
    private RedisOperator getRedisOperator(TtsCacheRepositoryImpl repository) {
        return (RedisOperator) ReflectionTestUtils.getField(repository, "redisOperator");
    }

    /**
     * 获取 Base64 大 key 专用 Redis 组件。
     *
     * @param repository 仓储
     * @return Redis 组件
     */
    private RedisOperator getAudioCacheRedisOperator(TtsCacheRepositoryImpl repository) {
        return (RedisOperator) ReflectionTestUtils.getField(repository, "audioCacheRedisOperator");
    }

    /**
     * 获取指标采集器。
     *
     * @param repository 仓储
     * @return 指标采集器
     */
    private TtsMetricsCollector getMetricsCollector(TtsCacheRepositoryImpl repository) {
        return (TtsMetricsCollector) ReflectionTestUtils.getField(repository, "ttsMetricsCollector");
    }

    /**
     * 挂载日志采集器。
     *
     * @return 日志采集器
     */
    private ListAppender<ILoggingEvent> attachListAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(TtsCacheRepositoryImpl.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        return listAppender;
    }

    /**
     * 释放日志采集器。
     *
     * @param listAppender 日志采集器
     */
    private void detachListAppender(ListAppender<ILoggingEvent> listAppender) {
        Logger logger = (Logger) LoggerFactory.getLogger(TtsCacheRepositoryImpl.class);
        logger.detachAppender(listAppender);
    }

    /**
     * 判断日志中是否包含目标文本。
     *
     * @param listAppender 日志采集器
     * @param text 目标文本
     * @return 是否包含
     */
    private boolean containsLog(ListAppender<ILoggingEvent> listAppender, String text) {
        return listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.contains(text));
    }
}
