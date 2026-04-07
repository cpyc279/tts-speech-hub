package com.tts.speech.deploy.modules.tts.infrastructure.external.feign.fallback;

import com.tts.speech.deploy.modules.tts.domain.constant.TtsVendorConstant;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.ThsLargeTtsRespDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.ThsTtsLargeFeignClient;
import org.springframework.stereotype.Component;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * 大模型 TTS 降级工厂。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Component
public class ThsTtsLargeFallbackFactory implements FallbackFactory<ThsTtsLargeFeignClient> {

    private static final String LARGE_MODEL_FALLBACK_MESSAGE_PREFIX = "large model tts fallback: ";

    /**
     * 创建降级实现。
     *
     * @param cause 异常
     * @return 降级实现
     */
    @Override
    public ThsTtsLargeFeignClient create(Throwable cause) {
        return (authorization, requestDTO) -> ThsLargeTtsRespDTO.builder()
            .code(TtsVendorConstant.FALLBACK_ERROR_CODE)
            .msg(LARGE_MODEL_FALLBACK_MESSAGE_PREFIX + cause.getMessage())
            .build();
    }
}
