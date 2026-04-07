package com.tts.speech.deploy.modules.tts.infrastructure.external.feign.config;

import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechNerProperties;
import feign.Request;
import feign.Retryer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * NerFeignClientConfig 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-23 00:00:00
 */
class NerFeignClientConfigTest {

    /**
     * 验证 NER Feign 显式关闭重试。
     */
    @Test
    void testNerFeignRetryerShouldNeverRetry() {
        NerFeignClientConfig config = new NerFeignClientConfig();

        Retryer retryer = config.nerFeignRetryer();

        Assertions.assertSame(Retryer.NEVER_RETRY, retryer);
    }

    /**
     * 验证 NER 非法超时配置回退默认值。
     */
    @Test
    void testNerFeignRequestOptionsShouldFallbackWhenTimeoutIsInvalid() {
        NerFeignClientConfig config = new NerFeignClientConfig();
        SpeechNerProperties properties = new SpeechNerProperties();

        // 非法超时配置应回退为默认值。
        properties.setTimeoutMs(0);
        // 注入配置后验证请求超时兜底逻辑。
        ReflectionTestUtils.setField(config, "speechNerProperties", properties);

        Request.Options options = config.nerFeignRequestOptions();

        Assertions.assertEquals(1000, options.connectTimeoutMillis());
        Assertions.assertEquals(1000, options.readTimeoutMillis());
    }
}
