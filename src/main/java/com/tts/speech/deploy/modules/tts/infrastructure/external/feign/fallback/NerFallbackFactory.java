package com.tts.speech.deploy.modules.tts.infrastructure.external.feign.fallback;

import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.NerFeignClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * NER 降级工厂。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Component
public class NerFallbackFactory implements FallbackFactory<NerFeignClient> {

    /**
     * NER 降级消息前缀。
     */
    private static final String NER_FALLBACK_MESSAGE_PREFIX = "ner fallback: ";

    /**
     * 创建降级实现。
     *
     * @param cause 异常
     * @return 降级实现
     */
    @Override
    public NerFeignClient create(Throwable cause) {
        return requestDTO -> {
            // 先构造统一的降级消息，保证不同失败来源的文案格式一致。
            String fallbackMessage = buildFallbackMessage(cause);
            // 直接抛出异常，避免降级结果被误识别为“NER 正常返回但未识别到人名”。
            throw new IllegalStateException(fallbackMessage, cause);
        };
    }

    /**
     * 构建 NER 降级异常消息。
     *
     * @param cause 原始异常
     * @return 降级异常消息
     */
    private static String buildFallbackMessage(Throwable cause) {
        // 原始异常为空时使用 unknown 占位，避免拼接 null 文案。
        if (cause == null) {
            return NER_FALLBACK_MESSAGE_PREFIX + "unknown";
        }
        // 原始异常消息为空时同样使用 unknown 占位，保证日志和异常文案可读。
        if (cause.getMessage() == null) {
            return NER_FALLBACK_MESSAGE_PREFIX + "unknown";
        }
        // 存在明确异常消息时直接拼接，保留最关键的下游失败信息。
        return NER_FALLBACK_MESSAGE_PREFIX + cause.getMessage();
    }
}
