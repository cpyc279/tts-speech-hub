package com.tts.speech.deploy.modules.tts.infrastructure.external.feign.fallback;

import com.tts.speech.deploy.modules.tts.domain.constant.TtsVendorConstant;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.ThsSmallTtsRespDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.ThsTtsSmallFeignClient;
import org.springframework.stereotype.Component;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * 小模型 TTS 降级工厂。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Component
public class ThsTtsSmallFallbackFactory implements FallbackFactory<ThsTtsSmallFeignClient> {

    private static final String SMALL_MODEL_FALLBACK_MESSAGE_PREFIX = "small model tts fallback: ";

    /**
     * 创建降级实现。
     *
     * @param cause 异常
     * @return 降级实现
     */
    @Override
    public ThsTtsSmallFeignClient create(Throwable cause) {
        return (param, timestamp, secretKey) -> ThsSmallTtsRespDTO.builder()
            .code(TtsVendorConstant.FALLBACK_ERROR_CODE)
            .note(SMALL_MODEL_FALLBACK_MESSAGE_PREFIX + cause.getMessage())
            .build();
    }
}
