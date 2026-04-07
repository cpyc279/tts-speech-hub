package com.tts.speech.deploy.modules.tts.domain.repository;

import com.tts.speech.deploy.modules.tts.domain.entity.TtsRouteEntity;

/**
 * 券商音色路由仓储接口。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
public interface BrokerVoiceRouteRepository {

    /**
     * 解析券商路由。
     *
     * @param brokerId 券商 ID
     * @return 路由结果
     */
    TtsRouteEntity resolveRoute(String brokerId);
}
