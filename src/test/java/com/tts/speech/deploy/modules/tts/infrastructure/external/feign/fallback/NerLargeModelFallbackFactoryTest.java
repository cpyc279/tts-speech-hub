package com.tts.speech.deploy.modules.tts.infrastructure.external.feign.fallback;

import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.NerLargeModelReqDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * NerLargeModelFallbackFactory 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-26 00:00:00
 */
class NerLargeModelFallbackFactoryTest {

    /**
     * 验证原始异常为空时使用 unknown 占位消息。
     */
    @Test
    void testCreateShouldUseUnknownMessageWhenCauseIsNull() {
        NerLargeModelFallbackFactory fallbackFactory = new NerLargeModelFallbackFactory();

        IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> fallbackFactory.create(null).chat(
                "127.0.0.1",
                "service-a",
                "session-1",
                "trace-1",
                "user-a",
                NerLargeModelReqDTO.builder().mode("WORK_FLOW").build()));

        Assertions.assertEquals("ner large model fallback: unknown", exception.getMessage());
    }

    /**
     * 验证原始异常消息为空时使用 unknown 占位消息。
     */
    @Test
    void testCreateShouldUseUnknownMessageWhenCauseMessageIsNull() {
        NerLargeModelFallbackFactory fallbackFactory = new NerLargeModelFallbackFactory();

        IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> fallbackFactory.create(new IllegalStateException()).chat(
                "127.0.0.1",
                "service-a",
                "session-1",
                "trace-1",
                "user-a",
                NerLargeModelReqDTO.builder().mode("WORK_FLOW").build()));

        Assertions.assertEquals("ner large model fallback: unknown", exception.getMessage());
    }

    /**
     * 验证原始异常消息存在时保留原始消息。
     */
    @Test
    void testCreateShouldKeepCauseMessageWhenCauseMessageExists() {
        NerLargeModelFallbackFactory fallbackFactory = new NerLargeModelFallbackFactory();

        IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> fallbackFactory.create(new IllegalStateException("timeout")).chat(
                "127.0.0.1",
                "service-a",
                "session-1",
                "trace-1",
                "user-a",
                NerLargeModelReqDTO.builder().mode("WORK_FLOW").build()));

        Assertions.assertEquals("ner large model fallback: timeout", exception.getMessage());
    }
}
