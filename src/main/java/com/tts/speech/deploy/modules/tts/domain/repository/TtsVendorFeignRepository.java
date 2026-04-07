package com.tts.speech.deploy.modules.tts.domain.repository;

import com.tts.speech.deploy.common.enums.TtsEndpointEnum;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsPreparedTextEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsVendorResponseEntity;

/**
 * TTS 下游调用接口。
 *
 * @author yangchen5
 * @since 2026-03-06 20:20:00
 */
public interface TtsVendorFeignRepository {

    /**
     * 调用指定下游端点进行单条文本合成。
     *
     * @param endpointEnum 端点枚举
     * @param modelCode 模型编码
     * @param preparedTextEntity 预处理文本实体
     * @param traceId 链路追踪标识
     * @return 下游响应结果
     */
    TtsVendorResponseEntity synthesize(
        TtsEndpointEnum endpointEnum,
        String modelCode,
        TtsPreparedTextEntity preparedTextEntity,
        String traceId);
}
