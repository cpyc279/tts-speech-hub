package com.tts.speech.deploy.modules.tts.infrastructure.external.feign.config;

import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import feign.Request;
import feign.Retryer;
import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

/**
 * TTS Feign 公共配置。
 *
 * @author yangchen5
 * @since 2026-03-23 00:00:00
 */
public class TtsFeignClientConfig {

    /**
     * 默认 Feign 超时时间，单位毫秒。
     */
    private static final int DEFAULT_TIMEOUT_MS = 2000;

    /**
     * 默认最大尝试总次数，包含首次调用。
     */
    private static final int DEFAULT_RETRY_MAX_ATTEMPTS = 2;

    /**
     * Feign 最小重试间隔，单位毫秒。
     */
    private static final long RETRY_PERIOD_MS = 1L;

    @Resource
    private SpeechTtsProperties speechTtsProperties;

    /**
     * TTS Feign 超时配置。
     *
     * @return Feign 请求选项
     */
    @Bean
    public Request.Options ttsFeignRequestOptions() {
        // 统一使用 TTS Feign 配置中的超时参数，未配置时回退默认值。
        int timeoutMs = resolveTimeoutMs();
        // 连接超时和读取超时保持一致，避免同一客户端出现两套口径。
        return new Request.Options(timeoutMs, TimeUnit.MILLISECONDS, timeoutMs, TimeUnit.MILLISECONDS, true);
    }

    /**
     * TTS Feign 重试配置。
     *
     * @return Feign 重试器
     */
    @Bean
    public Retryer ttsFeignRetryer() {
        // 最大尝试总次数包含首次调用，默认值 2 表示失败后最多补重试 1 次。
        int retryMaxAttempts = resolveRetryMaxAttempts();
        // 重试间隔固定为最小值，仅将超时和重试次数暴露为业务配置项。
        return new Retryer.Default(RETRY_PERIOD_MS, RETRY_PERIOD_MS, retryMaxAttempts);
    }

    /**
     * 解析 Feign 超时时间。
     *
     * @return 超时时间
     */
    private int resolveTimeoutMs() {
        SpeechTtsProperties.FeignConfig feignConfig = speechTtsProperties.getFeign();
        // 配置节点为空时直接回退默认值，避免空指针影响启动。
        if (feignConfig == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        Integer timeoutMs = feignConfig.getTimeoutMs();
        // 非正数配置视为无效，统一回退到默认超时时间。
        if (timeoutMs == null || timeoutMs <= 0) {
            return DEFAULT_TIMEOUT_MS;
        }
        return timeoutMs;
    }

    /**
     * 解析最大尝试总次数。
     *
     * @return 最大尝试总次数
     */
    private int resolveRetryMaxAttempts() {
        SpeechTtsProperties.FeignConfig feignConfig = speechTtsProperties.getFeign();
        // 配置节点为空时直接回退默认值，确保客户端仍可用。
        if (feignConfig == null) {
            return DEFAULT_RETRY_MAX_ATTEMPTS;
        }
        Integer retryMaxAttempts = feignConfig.getRetryMaxAttempts();
        // 小于等于 1 时不满足“至少包含首调”的语义，统一回退默认值。
        if (retryMaxAttempts == null || retryMaxAttempts <= 1) {
            return DEFAULT_RETRY_MAX_ATTEMPTS;
        }
        return retryMaxAttempts;
    }
}
