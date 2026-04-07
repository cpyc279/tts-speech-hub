package com.tts.speech.deploy.modules.tts.infrastructure.repository.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hexin.speech.base.auth.util.AuthUtil;
import com.tts.speech.deploy.common.util.JsonUtil;
import com.tts.speech.deploy.common.util.Md5Util;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * TtsVendorAuthRepositoryImpl 鍗曞厓娴嬭瘯銆?
 *
 * @author yangchen5
 * @since 2026-03-19 17:00:00
 */
class TtsVendorAuthRepositoryImplTest {

    /**
     * 楠岃瘉灏忔ā鍨嬭〃鍗曞弬鏁版寜閰嶇疆鏋勫缓銆?
     */
    @Test
    void testBuildSmallModelFormShouldUseConfiguredValue() {
        TtsVendorAuthRepositoryImpl repository = new TtsVendorAuthRepositoryImpl();
        ReflectionTestUtils.setField(repository, "speechTtsProperties", buildSpeechTtsProperties());

        Map<String, String> formMap = repository.buildSmallModelForm("娴嬭瘯鏂囨湰");
        JsonNode paramNode = readJson(formMap.get("param"));

        Assertions.assertEquals("娴嬭瘯鏂囨湰", paramNode.get("text").asText());
        Assertions.assertEquals("demo-engine", paramNode.get("engineName").asText());
        Assertions.assertEquals("xxx", paramNode.get("appId").asText());
        Assertions.assertEquals(1, paramNode.get("audioType").asInt());
        Assertions.assertEquals(2, paramNode.get("pitch").asInt());
        Assertions.assertEquals(88, paramNode.get("vol").asInt());
        Assertions.assertEquals(16000, paramNode.get("samplingRate").asInt());
        Assertions.assertEquals(24, paramNode.get("sampleDepth").asInt());
        Assertions.assertEquals(120, paramNode.get("speed").asInt());
        Assertions.assertEquals(
            Md5Util.md5("xxx" + formMap.get("param") + formMap.get("ts")),
            formMap.get("secretKey"));
    }

    /**
     * 楠岃瘉澶фā鍨嬮壌鏉冩敼涓哄鐢ㄥ閮ㄩ壌鏉冨伐鍏枫€?
     */
    @Test
    void testBuildLargeModelAuthorizationTokenShouldUseExternalAuthUtil() {
        String authorization = TtsVendorAuthRepositoryImpl.buildLargeModelAuthorizationToken(
            "xxx",
            "xxx",
            123456789L,
            "demo-nonce",
            "/hapi/v1/tts",
            "{\"text\":\"demo\"}");

        Assertions.assertEquals(
            AuthUtil.genAuthForPostString(
                "xxx",
                "xxx",
                123456789L,
                "demo-nonce",
                "/hapi/v1/tts",
                "{\"text\":\"demo\"}"),
            authorization);
    }

    /**
     * 鏋勫缓 TTS 閰嶇疆銆?
     *
     * @return TTS 閰嶇疆
     */
    private static SpeechTtsProperties buildSpeechTtsProperties() {
        SpeechTtsProperties speechTtsProperties = new SpeechTtsProperties();
        SpeechTtsProperties.EndpointConfig endpointConfig = new SpeechTtsProperties.EndpointConfig();
        endpointConfig.setAppId("xxx");
        endpointConfig.setAppKey("xxx");
        endpointConfig.setSmallModelEngineName("demo-engine");
        endpointConfig.setLargeModelPath("/hapi/v1/tts");
        SpeechTtsProperties.SmallModelFormConfig smallModelFormConfig = new SpeechTtsProperties.SmallModelFormConfig();
        smallModelFormConfig.setAudioType(1);
        smallModelFormConfig.setPitch(2);
        smallModelFormConfig.setVolume(88);
        smallModelFormConfig.setSamplingRate(16000);
        smallModelFormConfig.setSampleDepth(24);
        smallModelFormConfig.setSpeed(120);
        endpointConfig.setSmallModelForm(smallModelFormConfig);
        speechTtsProperties.setEndpoint(endpointConfig);
        return speechTtsProperties;
    }

    /**
     * 瑙ｆ瀽 JSON 瀛楃涓层€?
     *
     * @param json JSON 瀛楃涓?
     * @return JSON 鑺傜偣
     */
    private static JsonNode readJson(String json) {
        // 鍗曞厓娴嬭瘯鐩存帴澶嶇敤椤圭洰 JSON 宸ュ叿瑙ｆ瀽鍙傛暟鍐呭锛屼究浜庨€愰」鏂█閰嶇疆鏄犲皠缁撴灉銆?
        return JsonUtil.readTree(json);
    }
}
