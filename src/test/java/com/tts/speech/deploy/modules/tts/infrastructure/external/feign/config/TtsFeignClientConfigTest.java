package com.tts.speech.deploy.modules.tts.infrastructure.external.feign.config;

import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import feign.Request;
import feign.Retryer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * TtsFeignClientConfig 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-23 00:00:00
 */
class TtsFeignClientConfigTest {

    /**
     * 验证未配置时使用默认超时和默认最大尝试次数。
     */
    @Test
    void testTtsFeignConfigShouldUseDefaultValuesWhenConfigIsMissing() {
        TtsFeignClientConfig config = new TtsFeignClientConfig();
        SpeechTtsProperties properties = new SpeechTtsProperties();

        // 注入 TTS 配置，模拟业务未显式提供 Feign 参数的场景。
        ReflectionTestUtils.setField(config, "speechTtsProperties", properties);

        Request.Options options = config.ttsFeignRequestOptions();
        Retryer retryer = config.ttsFeignRetryer();

        Assertions.assertEquals(2000, options.connectTimeoutMillis());
        Assertions.assertEquals(2000, options.readTimeoutMillis());
        Assertions.assertEquals(2, ReflectionTestUtils.getField(retryer, "maxAttempts"));
    }

    /**
     * 验证显式配置的超时和最大尝试次数能够生效。
     */
    @Test
    void testTtsFeignConfigShouldUseConfiguredValuesWhenConfigExists() {
        TtsFeignClientConfig config = new TtsFeignClientConfig();
        SpeechTtsProperties properties = new SpeechTtsProperties();
        SpeechTtsProperties.FeignConfig feignConfig = new SpeechTtsProperties.FeignConfig();

        // 设置业务期望的超时和总尝试次数。
        feignConfig.setTimeoutMs(3200);
        feignConfig.setRetryMaxAttempts(3);
        properties.setFeign(feignConfig);
        // 注入配置后校验 Bean 解析结果。
        ReflectionTestUtils.setField(config, "speechTtsProperties", properties);

        Request.Options options = config.ttsFeignRequestOptions();
        Retryer retryer = config.ttsFeignRetryer();

        Assertions.assertEquals(3200, options.connectTimeoutMillis());
        Assertions.assertEquals(3200, options.readTimeoutMillis());
        Assertions.assertEquals(3, ReflectionTestUtils.getField(retryer, "maxAttempts"));
    }

    /**
     * 验证非法配置会回退到默认值。
     */
    @Test
    void testTtsFeignConfigShouldFallbackWhenConfigIsInvalid() {
        TtsFeignClientConfig config = new TtsFeignClientConfig();
        SpeechTtsProperties properties = new SpeechTtsProperties();
        SpeechTtsProperties.FeignConfig feignConfig = new SpeechTtsProperties.FeignConfig();

        // 非法超时和非法最大尝试次数都应该回退为默认值。
        feignConfig.setTimeoutMs(0);
        feignConfig.setRetryMaxAttempts(1);
        properties.setFeign(feignConfig);
        // 注入配置后验证兜底逻辑。
        ReflectionTestUtils.setField(config, "speechTtsProperties", properties);

        Request.Options options = config.ttsFeignRequestOptions();
        Retryer retryer = config.ttsFeignRetryer();

        Assertions.assertEquals(2000, options.connectTimeoutMillis());
        Assertions.assertEquals(2000, options.readTimeoutMillis());
        Assertions.assertEquals(2, ReflectionTestUtils.getField(retryer, "maxAttempts"));
    }
}
