package com.tts.speech.deploy.modules.tts.infrastructure.config;

import io.lettuce.core.ClientOptions;
import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * TtsRedisConfig 单元测试。
 *
 * @author yangchen5
 * @since 2026-04-03 16:05:00
 */
class TtsRedisConfigTest {

    /**
     * 验证 Base64 专用通道在显式配置时会使用独立连接超时。
     */
    @Test
    void testAudioCacheRedisConnectionFactoryShouldUseDedicatedConnectTimeoutWhenConfigured() {
        TtsRedisConfig config = new TtsRedisConfig();
        RedisProperties redisProperties = buildRedisProperties();
        SpeechTtsProperties speechTtsProperties = buildSpeechTtsProperties();
        speechTtsProperties.getCache().setBase64ConnectTimeoutMs(350);

        LettuceConnectionFactory connectionFactory =
            config.audioCacheRedisConnectionFactory(redisProperties, speechTtsProperties);

        // Base64 专用通道命中了独立配置时，应优先使用独立连接超时。
        Assertions.assertEquals(
            Duration.ofMillis(350),
            getConnectTimeout(connectionFactory),
            "Base64 专用 Redis 通道应优先使用独立 connect-timeout 配置");
        connectionFactory.destroy();
    }

    /**
     * 验证 Base64 专用通道在未显式配置时会回退到全局连接超时。
     */
    @Test
    void testAudioCacheRedisConnectionFactoryShouldFallbackToGlobalConnectTimeoutWhenDedicatedMissing() {
        TtsRedisConfig config = new TtsRedisConfig();
        RedisProperties redisProperties = buildRedisProperties();
        SpeechTtsProperties speechTtsProperties = buildSpeechTtsProperties();

        LettuceConnectionFactory connectionFactory =
            config.audioCacheRedisConnectionFactory(redisProperties, speechTtsProperties);

        // 未配置独立连接超时时，应继承全局 Redis connect-timeout，避免专用通道行为漂移。
        Assertions.assertEquals(
            Duration.ofMillis(200),
            getConnectTimeout(connectionFactory),
            "Base64 专用 Redis 通道未配置独立 connect-timeout 时应继承全局配置");
        connectionFactory.destroy();
    }

    /**
     * 构建 Redis 基础配置。
     *
     * @return Redis 配置
     */
    private static RedisProperties buildRedisProperties() {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setHost("127.0.0.1");
        redisProperties.setPort(6379);
        redisProperties.setDatabase(0);
        redisProperties.setConnectTimeout(Duration.ofMillis(200));
        return redisProperties;
    }

    /**
     * 构建 TTS 配置。
     *
     * @return TTS 配置
     */
    private static SpeechTtsProperties buildSpeechTtsProperties() {
        SpeechTtsProperties speechTtsProperties = new SpeechTtsProperties();
        speechTtsProperties.getCache().setBase64CommandTimeoutMs(500);
        return speechTtsProperties;
    }

    /**
     * 获取连接工厂中的连接超时配置。
     *
     * @param connectionFactory Lettuce 连接工厂
     * @return 连接超时
     */
    private static Duration getConnectTimeout(LettuceConnectionFactory connectionFactory) {
        ClientOptions clientOptions = connectionFactory.getClientConfiguration()
            .getClientOptions()
            .orElseThrow(() -> new IllegalStateException("Lettuce ClientOptions 不应为空"));
        return clientOptions.getSocketOptions().getConnectTimeout();
    }
}
