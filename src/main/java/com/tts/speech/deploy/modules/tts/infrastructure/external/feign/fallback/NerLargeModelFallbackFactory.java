package com.tts.speech.deploy.modules.tts.infrastructure.external.feign.fallback;

import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.NerLargeModelFeignClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 新版 NER 大模型降级工厂。
 *
 * @author yangchen5
 * @since 2026-03-26 00:00:00
 */
@Component
public class NerLargeModelFallbackFactory implements FallbackFactory<NerLargeModelFeignClient> {

    /**
     * 降级消息前缀。
     */
    private static final String NER_LARGE_MODEL_FALLBACK_MESSAGE_PREFIX = "ner large model fallback: ";

    /**
     * 创建降级实现。
     *
     * @param cause 异常原因
     * @return 降级实现
     */
    @Override
    public NerLargeModelFeignClient create(Throwable cause) {
        return (remoteIp, remoteSvc, sessionId, traceId, userId, requestDTO) -> {
            // 统一抛出异常，避免调用方把降级结果误判为正常业务返回。
            String fallbackMessage = buildFallbackMessage(cause);
            throw new IllegalStateException(fallbackMessage, cause);
        };
    }

    /**
     * 构建降级消息。
     *
     * @param cause 原始异常
     * @return 降级消息
     */
    private static String buildFallbackMessage(Throwable cause) {
        if (cause == null) {
            return NER_LARGE_MODEL_FALLBACK_MESSAGE_PREFIX + "unknown";
        }
        if (cause.getMessage() == null) {
            return NER_LARGE_MODEL_FALLBACK_MESSAGE_PREFIX + "unknown";
        }
        return NER_LARGE_MODEL_FALLBACK_MESSAGE_PREFIX + cause.getMessage();
    }
}
