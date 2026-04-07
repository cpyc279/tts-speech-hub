package com.tts.speech.deploy.modules.tts.infrastructure.external.feign.fallback;

import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.NerReqDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * NerFallbackFactory 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-19 16:00:00
 */
class NerFallbackFactoryTest {

    /**
     * 验证原始异常为空时使用 unknown 占位消息。
     */
    @Test
    void testCreateShouldUseUnknownMessageWhenCauseIsNull() {
        NerFallbackFactory fallbackFactory = new NerFallbackFactory();

        IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> fallbackFactory.create(null).extract(NerReqDTO.builder().text("测试").build()));

        Assertions.assertEquals("ner fallback: unknown", exception.getMessage());
    }

    /**
     * 验证原始异常消息为空时使用 unknown 占位消息。
     */
    @Test
    void testCreateShouldUseUnknownMessageWhenCauseMessageIsNull() {
        NerFallbackFactory fallbackFactory = new NerFallbackFactory();

        IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> fallbackFactory.create(new IllegalStateException()).extract(NerReqDTO.builder().text("测试").build()));

        Assertions.assertEquals("ner fallback: unknown", exception.getMessage());
    }

    /**
     * 验证原始异常消息存在时保留原始信息。
     */
    @Test
    void testCreateShouldKeepCauseMessageWhenCauseMessageExists() {
        NerFallbackFactory fallbackFactory = new NerFallbackFactory();

        IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> fallbackFactory.create(new IllegalStateException("timeout"))
                .extract(NerReqDTO.builder().text("测试").build()));

        Assertions.assertEquals("ner fallback: timeout", exception.getMessage());
    }
}
