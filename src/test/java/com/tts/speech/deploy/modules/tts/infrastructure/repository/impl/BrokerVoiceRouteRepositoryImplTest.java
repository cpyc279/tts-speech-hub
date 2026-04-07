package com.tts.speech.deploy.modules.tts.infrastructure.repository.impl;

import com.tts.speech.deploy.common.enums.TtsEndpointEnum;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRouteEntity;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * BrokerVoiceRouteRepositoryImpl 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-20 10:00:00
 */
class BrokerVoiceRouteRepositoryImplTest {

    /**
     * 验证路由解析会返回配置中的模型编码。
     */
    @Test
    void testResolveRouteShouldReturnModelCode() {
        BrokerVoiceRouteRepositoryImpl repository = createRepository();

        // 券商命中专属配置时，应返回券商绑定的模型编码和主备端点。
        TtsRouteEntity routeEntity = repository.resolveRoute("broker-a");

        Assertions.assertEquals("hithink", routeEntity.getModelCode());
        Assertions.assertEquals(TtsEndpointEnum.SMALL, routeEntity.getPrimaryEndpoint());
        Assertions.assertEquals(TtsEndpointEnum.LARGE, routeEntity.getFallbackEndpoint());
    }

    /**
     * 验证券商未命中时会回退默认模型编码。
     */
    @Test
    void testResolveRouteShouldFallbackToDefaultModelCode() {
        BrokerVoiceRouteRepositoryImpl repository = createRepository();

        // 券商未配置专属模型时，应回退默认模型编码并按默认模型解析主备端点。
        TtsRouteEntity routeEntity = repository.resolveRoute("unknown-broker");

        Assertions.assertEquals("hithink", routeEntity.getModelCode());
        Assertions.assertEquals(TtsEndpointEnum.SMALL, routeEntity.getPrimaryEndpoint());
        Assertions.assertEquals(TtsEndpointEnum.LARGE, routeEntity.getFallbackEndpoint());
    }

    /**
     * 创建路由仓储。
     *
     * @return 路由仓储
     */
    private BrokerVoiceRouteRepositoryImpl createRepository() {
        BrokerVoiceRouteRepositoryImpl repository = new BrokerVoiceRouteRepositoryImpl();
        // 路由测试只需要注入最小配置集合，避免引入其他无关依赖。
        ReflectionTestUtils.setField(repository, "speechTtsProperties", buildSpeechTtsProperties());
        return repository;
    }

    /**
     * 构建 TTS 配置。
     *
     * @return TTS 配置
     */
    private SpeechTtsProperties buildSpeechTtsProperties() {
        SpeechTtsProperties speechTtsProperties = new SpeechTtsProperties();
        SpeechTtsProperties.VoiceRouteConfig voiceRouteConfig = new SpeechTtsProperties.VoiceRouteConfig();
        // 默认模型编码用于兜底未知券商的路由解析。
        voiceRouteConfig.setDefaultModelCode("hithink");
        // 券商模型映射沿用当前业务中的模型标识。
        voiceRouteConfig.setBrokerModelCodeMap(Map.of("broker-a", "hithink"));
        // 主备端点都按照模型编码维度配置。
        voiceRouteConfig.setPrimaryEndpointMap(Map.of("hithink", "SMALL"));
        voiceRouteConfig.setFallbackEndpointMap(Map.of("hithink", "LARGE"));
        speechTtsProperties.setVoiceRoute(voiceRouteConfig);
        return speechTtsProperties;
    }
}
