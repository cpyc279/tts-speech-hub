package com.tts.speech.deploy.app.manager.impl;

import com.tts.speech.deploy.app.converter.TtsResponseConverter;
import com.tts.speech.deploy.app.dto.TtsGenerateDataDTO;
import com.tts.speech.deploy.app.manager.TtsManager;
import com.tts.speech.deploy.app.shared.auth.SignAuthService;
import com.tts.speech.deploy.common.exception.InvalidParameterException;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRequestEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsResponseEntity;
import com.tts.speech.deploy.modules.tts.domain.repository.TtsCacheRepository;
import com.tts.speech.deploy.modules.tts.domain.service.TtsDomainService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * TTS 编排实现。
 *
 * @author yangchen5
 * @since 2026-03-06 20:05:00
 */
@Component
public class TtsManagerImpl implements TtsManager {

    /**
     * 请求对象为空提示。
     */
    private static final String REQUEST_NULL_MESSAGE = "tts request must not be null";

    @Resource
    private TtsDomainService ttsDomainService;

    @Resource
    private TtsResponseConverter ttsResponseConverter;

    @Resource
    private SignAuthService signAuthService;

    @Resource
    private TtsCacheRepository ttsCacheRepository;

    /**
     * 获取本次请求的 traceId。
     *
     * @param userId 用户ID
     * @return traceId
     */
    @Override
    public String getTraceId(String userId) {
        // 按 userId 复用短周期 traceId，空 userId 场景由缓存仓储直接兜底生成。
        return ttsCacheRepository.getOrCreateTraceId(userId);
    }

    /**
     * 执行 TTS 编排。
     *
     * @param requestEntity TTS 请求
     * @return TTS 响应
     */
    @Override
    public TtsGenerateDataDTO synthesize(TtsRequestEntity requestEntity) {
        // 先做对象级空值校验，避免后续访问请求字段时抛出空指针异常。
        if (requestEntity == null) {
            throw new InvalidParameterException(REQUEST_NULL_MESSAGE);
        }
        // controller 已经补齐 traceId，这里直接执行签名校验。
        signAuthService.validateTtsSign(requestEntity);
        // 进入领域服务执行完整的 TTS 合成流程，并组装对外响应结果。
        TtsResponseEntity ttsResponseEntity = ttsDomainService.synthesize(requestEntity);
        return ttsResponseConverter.toDataDTO(ttsResponseEntity);
    }
}
