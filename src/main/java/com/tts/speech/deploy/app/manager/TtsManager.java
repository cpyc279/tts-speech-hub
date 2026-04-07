package com.tts.speech.deploy.app.manager;

import com.tts.speech.deploy.app.dto.TtsGenerateDataDTO;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRequestEntity;

/**
 * TTS 编排接口。
 *
 * @author yangchen5
 * @since 2026-03-06 20:05:00
 */
public interface TtsManager {

    /**
     * 获取本次请求的 traceId。
     *
     * @param userId 用户ID
     * @return traceId
     */
    String getTraceId(String userId);

    /**
     * 执行 TTS 语音合成编排。
     *
     * @param requestEntity TTS 请求
     * @return TTS 响应
     */
    TtsGenerateDataDTO synthesize(TtsRequestEntity requestEntity);
}
