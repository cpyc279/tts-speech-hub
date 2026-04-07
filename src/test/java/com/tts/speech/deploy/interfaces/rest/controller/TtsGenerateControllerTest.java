package com.tts.speech.deploy.interfaces.rest.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tts.speech.deploy.app.converter.TtsApiConverter;
import com.tts.speech.deploy.app.dto.ApiResponseDTO;
import com.tts.speech.deploy.app.dto.TtsGenerateDataDTO;
import com.tts.speech.deploy.app.manager.TtsManager;
import com.tts.speech.deploy.interfaces.rest.req.TtsGenerateReq;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRequestEntity;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * TtsGenerateController 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-28 15:20:00
 */
class TtsGenerateControllerTest {

    /**
     * 验证控制器会先获取 traceId，再打印非空请求参数日志。
     */
    @Test
    void testGenerateShouldLogRequestWithTraceIdAndNonEmptyFields() {
        TtsGenerateController controller = new TtsGenerateController();
        TtsManager ttsManager = Mockito.mock(TtsManager.class);
        TtsApiConverter ttsApiConverter = Mockito.mock(TtsApiConverter.class);
        TtsRequestEntity requestEntity = TtsRequestEntity.builder().build();
        TtsGenerateDataDTO dataDTO = Mockito.mock(TtsGenerateDataDTO.class);
        ListAppender<ILoggingEvent> listAppender = attachListAppender();
        ReflectionTestUtils.setField(controller, "ttsManager", ttsManager);
        ReflectionTestUtils.setField(controller, "ttsApiConverter", ttsApiConverter);
        TtsGenerateReq request = TtsGenerateReq.builder()
            .sign("sign-value")
            .timestamp("1743148800")
            .brokerId("broker-a")
            .userId("user-a")
            .rawTextList(List.of("欢迎张三办理业务"))
            .build();
        Mockito.when(ttsManager.getTraceId("user-a")).thenReturn("trace-1");
        Mockito.when(ttsApiConverter.toEntity(request)).thenReturn(requestEntity);
        Mockito.when(ttsManager.synthesize(requestEntity)).thenReturn(dataDTO);

        ApiResponseDTO<TtsGenerateDataDTO> responseDTO = controller.generate(request);

        Assertions.assertNotNull(responseDTO);
        Assertions.assertEquals("trace-1", requestEntity.getTraceId());
        Assertions.assertTrue(
            containsLog(listAppender, "traceId=trace-1"),
            "控制器请求日志应打印 traceId");
        Assertions.assertTrue(
            containsLog(listAppender, "brokerId=broker-a"),
            "控制器请求日志应打印非空请求参数");
        Assertions.assertTrue(
            containsLog(listAppender, "rawTextList=[欢迎张三办理业务]"),
            "控制器请求日志应打印原始文本");
        Assertions.assertFalse(
            containsLog(listAppender, "sessionId"),
            "控制器请求日志不应打印 sessionId");
        Assertions.assertFalse(
            containsLog(listAppender, "businessCode"),
            "空值参数不应出现在日志中");
        detachListAppender(listAppender);
    }

    /**
     * 挂载日志收集器。
     *
     * @return 日志收集器
     */
    private ListAppender<ILoggingEvent> attachListAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(TtsGenerateController.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        return listAppender;
    }

    /**
     * 释放日志收集器。
     *
     * @param listAppender 日志收集器
     */
    private void detachListAppender(ListAppender<ILoggingEvent> listAppender) {
        Logger logger = (Logger) LoggerFactory.getLogger(TtsGenerateController.class);
        logger.detachAppender(listAppender);
    }

    /**
     * 判断日志中是否包含目标文本。
     *
     * @param listAppender 日志收集器
     * @param text 目标文本
     * @return 是否包含
     */
    private boolean containsLog(ListAppender<ILoggingEvent> listAppender, String text) {
        return listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.contains(text));
    }
}
