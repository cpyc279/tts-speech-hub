package com.tts.speech.deploy.modules.tts.infrastructure.repository.impl;

import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import com.tts.speech.deploy.common.enums.TtsEndpointEnum;
import com.tts.speech.deploy.common.exception.BizAssert;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRouteEntity;
import com.tts.speech.deploy.modules.tts.domain.repository.BrokerVoiceRouteRepository;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 券商音色路由仓储实现。
 *
 * @author yangchen5
 * @since 2026-03-06 18:08:56
 */
@Component
public class BrokerVoiceRouteRepositoryImpl implements BrokerVoiceRouteRepository {

    private static final String MODEL_CODE_MISSING_MESSAGE = "modelCode 配置缺失";
    private static final String PRIMARY_ENDPOINT_MISSING_MESSAGE = "首选 TTS 接口配置缺失";
    private static final String FALLBACK_ENDPOINT_MISSING_MESSAGE = "备用 TTS 接口配置缺失";

    @Resource
    private SpeechTtsProperties speechTtsProperties;

    /**
     * 解析券商路由。
     *
     * @param brokerId 券商 ID
     * @return 路由结果
     */
    @Override
    public TtsRouteEntity resolveRoute(String brokerId) {
        String modelCode = speechTtsProperties.getVoiceRoute().getBrokerModelCodeMap()
            .getOrDefault(brokerId, speechTtsProperties.getVoiceRoute().getDefaultModelCode());
        String primaryCode = speechTtsProperties.getVoiceRoute().getPrimaryEndpointMap().get(modelCode);
        String fallbackCode = speechTtsProperties.getVoiceRoute().getFallbackEndpointMap().get(modelCode);

        BizAssert.isTrue(StringUtils.hasText(modelCode), ErrorCodeEnum.TTS_ROUTE_MISSING, MODEL_CODE_MISSING_MESSAGE);
        BizAssert.notNull(TtsEndpointEnum.fromCode(primaryCode), ErrorCodeEnum.TTS_ROUTE_MISSING, PRIMARY_ENDPOINT_MISSING_MESSAGE);
        BizAssert.notNull(TtsEndpointEnum.fromCode(fallbackCode), ErrorCodeEnum.TTS_ROUTE_MISSING, FALLBACK_ENDPOINT_MISSING_MESSAGE);

        return TtsRouteEntity.builder()
            .modelCode(modelCode)
            .primaryEndpoint(TtsEndpointEnum.fromCode(primaryCode))
            .fallbackEndpoint(TtsEndpointEnum.fromCode(fallbackCode))
            .build();
    }
}
