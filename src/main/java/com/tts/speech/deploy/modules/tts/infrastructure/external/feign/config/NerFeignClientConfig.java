package com.tts.speech.deploy.modules.tts.infrastructure.external.feign.config;

import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechNerProperties;
import feign.Request;
import feign.Retryer;
import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

/**
 * NER Feign 配置。
 *
 * @author yangchen5
 * @since 2026-03-20 10:00:00
 */
public class NerFeignClientConfig {

    /**
     * 默认超时时间，单位毫秒。
     */
    private static final int DEFAULT_TIMEOUT_MS = 1000;

    @Resource
    private SpeechNerProperties speechNerProperties;

    /**
     * NER Feign 超时配置。
     *
     * @return Feign 请求选项
     */
    @Bean
    public Request.Options nerFeignRequestOptions() {
        // 优先使用业务配置的超时时间，未配置时回退到默认值。
        int timeoutMs = resolveTimeoutMs();
        // 连接超时和读取超时统一使用同一份配置，避免出现局部超时口径不一致。
        return new Request.Options(timeoutMs, TimeUnit.MILLISECONDS, timeoutMs, TimeUnit.MILLISECONDS, true);
    }

    /**
     * NER Feign 重试配置。
     *
     * @return Feign 重试器
     */
    @Bean
    public Retryer nerFeignRetryer() {
        // NER 请求显式关闭重试，避免重复调用放大下游压力。
        return Retryer.NEVER_RETRY;
    }

    /**
     * 解析超时时间。
     *
     * @return 超时时间
     */
    private int resolveTimeoutMs() {
        Integer timeoutMs = speechNerProperties.getTimeoutMs();
        // 非正数配置视为无效，统一回退到默认超时时间。
        if (timeoutMs == null || timeoutMs <= 0) {
            return DEFAULT_TIMEOUT_MS;
        }
        return timeoutMs;
    }
}
