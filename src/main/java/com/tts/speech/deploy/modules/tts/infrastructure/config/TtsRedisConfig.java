package com.tts.speech.deploy.modules.tts.infrastructure.config;

import com.tts.speech.deploy.common.metrics.TtsMetricsCollector;
import com.tts.speech.deploy.common.redis.RedisOperator;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.time.Duration;

import io.lettuce.core.protocol.ProtocolVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

/**
 * TTS Redis 配置。
 *
 * @author yangchen5
 * @since 2026-04-03 00:00:00
 */
@Configuration
public class TtsRedisConfig {

    /**
     * Base64 大 key 默认命令超时时间，单位毫秒。
     */
    private static final int DEFAULT_BASE64_COMMAND_TIMEOUT_MS = 500;

    /**
     * Base64 大 key 默认连接超时时间，单位毫秒。
     */
    private static final int DEFAULT_BASE64_CONNECT_TIMEOUT_MS = 200;

    /**
     * 创建 Base64 大 key 专用 Redis 连接工厂。
     *
     * @param redisProperties Spring Redis 配置
     * @param speechTtsProperties TTS 配置
     * @return Redis 连接工厂
     */
    @Bean("audioCacheRedisConnectionFactory")
    public LettuceConnectionFactory audioCacheRedisConnectionFactory(
        RedisProperties redisProperties,
        SpeechTtsProperties speechTtsProperties) {
        // Base64 大 key 需要放宽命令超时，但仍然复用同一个 Redis 实例与数据库配置。
        RedisStandaloneConfiguration standaloneConfiguration =
            buildStandaloneConfiguration(redisProperties);
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
            .clientOptions(buildBase64ClientOptions(redisProperties, speechTtsProperties))
            .commandTimeout(buildBase64CommandTimeout(speechTtsProperties))
            .build();

        // 独立连接工厂只服务于 Base64 value key，避免影响默认 200ms 通道。
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
            standaloneConfiguration,
            clientConfiguration);
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    /**
     * 创建 Base64 大 key 专用 RedisTemplate。
     *
     * @param connectionFactory Redis 连接工厂
     * @return RedisTemplate 组件
     */
    @Bean("audioCacheRedisTemplate")
    public RedisTemplate<String, String> audioCacheRedisTemplate(
        @Qualifier("audioCacheRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        // Base64 value 和现有缓存一样都按字符串读写，避免改动序列化协议。
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setValueSerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        redisTemplate.setHashValueSerializer(stringRedisSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 创建 Base64 大 key 专用 Redis 访问组件。
     *
     * @param redisTemplate Base64 大 key 专用 RedisTemplate
     * @param ttsMetricsCollector TTS Redis 降级指标采集器
     * @return Redis 访问组件
     */
    @Bean("audioCacheRedisOperator")
    public RedisOperator audioCacheRedisOperator(
        @Qualifier("audioCacheRedisTemplate") RedisTemplate<String, String> redisTemplate,
        TtsMetricsCollector ttsMetricsCollector) {
        // 复用既有 RedisOperator 的降级与指标逻辑，只切换底层超时配置。
        return new RedisOperator(redisTemplate, ttsMetricsCollector);
    }

    /**
     * 构建 Redis 单机配置。
     *
     * @param redisProperties Spring Redis 配置
     * @return Redis 单机配置
     */
    private static RedisStandaloneConfiguration buildStandaloneConfiguration(RedisProperties redisProperties) {
        // 当前服务使用单机 Redis 配置，这里与 Spring 默认 Redis 配置保持一致。
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisProperties.getHost());
        standaloneConfiguration.setPort(redisProperties.getPort());
        standaloneConfiguration.setDatabase(redisProperties.getDatabase());
        if (StringUtils.hasText(redisProperties.getUsername())) {
            standaloneConfiguration.setUsername(redisProperties.getUsername());
        }
        if (StringUtils.hasText(redisProperties.getPassword())) {
            standaloneConfiguration.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }
        return standaloneConfiguration;
    }

    /**
     * 构建 Base64 大 key 命令超时时间。
     *
     * @param speechTtsProperties TTS 配置
     * @return 命令超时时间
     */
    private static Duration buildBase64CommandTimeout(SpeechTtsProperties speechTtsProperties) {
        Integer timeoutMs = speechTtsProperties.getCache().getBase64CommandTimeoutMs();
        if (timeoutMs == null || timeoutMs <= 0) {
            return Duration.ofMillis(DEFAULT_BASE64_COMMAND_TIMEOUT_MS);
        }
        return Duration.ofMillis(timeoutMs);
    }

    /**
     * 构建 Base64 大 key 专用 ClientOptions。
     *
     * @param redisProperties Spring Redis 配置
     * @param speechTtsProperties TTS 配置
     * @return ClientOptions
     */
    private static ClientOptions buildBase64ClientOptions(
        RedisProperties redisProperties,
        SpeechTtsProperties speechTtsProperties) {
        // Base64 专用通道只额外定制连接超时，其他 Socket 行为保持 Lettuce 默认策略。
        SocketOptions socketOptions = SocketOptions.builder()
            .connectTimeout(buildBase64ConnectTimeout(redisProperties, speechTtsProperties))
            .build();
        return ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2)
                .socketOptions(socketOptions)
                .build();
    }

    /**
     * 构建 Base64 大 key 连接超时时间。
     *
     * @param redisProperties Spring Redis 配置
     * @param speechTtsProperties TTS 配置
     * @return 连接超时时间
     */
    private static Duration buildBase64ConnectTimeout(
        RedisProperties redisProperties,
        SpeechTtsProperties speechTtsProperties) {
        Integer timeoutMs = speechTtsProperties.getCache().getBase64ConnectTimeoutMs();
        if (timeoutMs != null && timeoutMs > 0) {
            return Duration.ofMillis(timeoutMs);
        }

        // 未配置独立 connect-timeout 时，回退到全局 Redis connect-timeout，避免专用通道行为漂移。
        Duration connectTimeout = redisProperties.getConnectTimeout();
        if (connectTimeout != null && !connectTimeout.isZero() && !connectTimeout.isNegative()) {
            return connectTimeout;
        }
        return Duration.ofMillis(DEFAULT_BASE64_CONNECT_TIMEOUT_MS);
    }
}
