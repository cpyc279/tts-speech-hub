package com.tts.speech.deploy.modules.tts.infrastructure.external.feign.config;

import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechNerProperties;
import feign.Request;
import feign.Retryer;
import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

/**
 * 新版 NER 大模型 Feign 配置。
 *
 * @author yangchen5
 * @since 2026-03-26 00:00:00
 */
public class NerLargeModelFeignClientConfig {

    /**
     * 默认超时时间，单位毫秒。
     */
    private static final int DEFAULT_TIMEOUT_MS = 2000;

    @Resource
    private SpeechNerProperties speechNerProperties;

    /**
     * 新版 NER 大模型超时配置。
     *
     * @return Feign 请求选项
     */
    @Bean
    public Request.Options nerLargeModelFeignRequestOptions() {
        // 优先使用新版 NER 专属超时配置，缺失时回退到默认值。
        int timeoutMs = resolveTimeoutMs();
        // 连接超时和读取超时统一使用同一份配置，避免口径不一致。
        return new Request.Options(timeoutMs, TimeUnit.MILLISECONDS, timeoutMs, TimeUnit.MILLISECONDS, true);
    }

    /**
     * 新版 NER 大模型重试配置。
     *
     * @return 重试器
     */
    @Bean
    public Retryer nerLargeModelFeignRetryer() {
        // 新版 NER 明确关闭自动重试，避免重复打到工作流服务。
        return Retryer.NEVER_RETRY;
    }

    /**
     * 解析超时时间。
     *
     * @return 超时时间
     */
    private int resolveTimeoutMs() {
        SpeechNerProperties.LargeModelConfig largeModelConfig = speechNerProperties.getLargeModel();
        if (largeModelConfig == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        Integer timeoutMs = largeModelConfig.getTimeoutMs();
        if (timeoutMs == null || timeoutMs <= 0) {
            return DEFAULT_TIMEOUT_MS;
        }
        return timeoutMs;
    }
}
