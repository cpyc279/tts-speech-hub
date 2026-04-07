package com.tts.speech.deploy.modules.tts.infrastructure.external.feign.config;

import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechNerProperties;
import feign.Request;
import feign.Retryer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * NerLargeModelFeignClientConfig 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-26 00:00:00
 */
class NerLargeModelFeignClientConfigTest {

    /**
     * 验证新版 NER Feign 显式关闭重试。
     */
    @Test
    void testNerLargeModelFeignRetryerShouldNeverRetry() {
        NerLargeModelFeignClientConfig config = new NerLargeModelFeignClientConfig();

        Retryer retryer = config.nerLargeModelFeignRetryer();

        Assertions.assertSame(Retryer.NEVER_RETRY, retryer);
    }

    /**
     * 验证新版 NER 非法超时配置时回退默认值。
     */
    @Test
    void testNerLargeModelFeignRequestOptionsShouldFallbackWhenTimeoutIsInvalid() {
        NerLargeModelFeignClientConfig config = new NerLargeModelFeignClientConfig();
        SpeechNerProperties properties = new SpeechNerProperties();
        SpeechNerProperties.LargeModelConfig largeModelConfig = new SpeechNerProperties.LargeModelConfig();
        largeModelConfig.setTimeoutMs(0);
        properties.setLargeModel(largeModelConfig);
        ReflectionTestUtils.setField(config, "speechNerProperties", properties);

        Request.Options options = config.nerLargeModelFeignRequestOptions();

        Assertions.assertEquals(2000, options.connectTimeoutMillis());
        Assertions.assertEquals(2000, options.readTimeoutMillis());
    }
}
