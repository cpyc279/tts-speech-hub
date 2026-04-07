package com.tts.speech.deploy.common.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tts.speech.deploy.app.dto.ApiResponseDTO;
import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import com.tts.speech.deploy.common.util.TraceIdUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * GlobalExceptionHandler 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-31 00:00:00
 */
class GlobalExceptionHandlerTest {

    /**
     * 验证兜底异常会打印异常日志，并将异常信息返回给调用方。
     */
    @Test
    void testHandleExceptionShouldLogExceptionAndReturnExceptionMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RuntimeException exception = new RuntimeException("数据库超时");
        ListAppender<ILoggingEvent> listAppender = attachListAppender();
        // 绑定 traceId，便于校验兜底日志会带出链路上下文。
        TraceIdUtil.bindTraceId("trace-1");

        ApiResponseDTO<Void> responseDTO = handler.handleException(exception);

        Assertions.assertEquals(ErrorCodeEnum.SYSTEM_ERROR.getCode(), responseDTO.getStatusCode());
        Assertions.assertEquals("数据库超时", responseDTO.getStatusMsg());
        Assertions.assertTrue(containsLog(listAppender, "traceId=trace-1"));
        Assertions.assertTrue(containsLog(listAppender, "数据库超时"));
        Assertions.assertTrue(containsThrowable(listAppender, RuntimeException.class));
        detachListAppender(listAppender);
        MDC.clear();
    }

    /**
     * 挂载日志采集器。
     *
     * @return 日志采集器
     */
    private ListAppender<ILoggingEvent> attachListAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        return listAppender;
    }

    /**
     * 释放日志采集器。
     *
     * @param listAppender 日志采集器
     */
    private void detachListAppender(ListAppender<ILoggingEvent> listAppender) {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logger.detachAppender(listAppender);
    }

    /**
     * 判断日志中是否包含目标文本。
     *
     * @param listAppender 日志采集器
     * @param text 目标文本
     * @return 是否包含
     */
    private boolean containsLog(ListAppender<ILoggingEvent> listAppender, String text) {
        return listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.contains(text));
    }

    /**
     * 判断日志中是否包含目标异常类型。
     *
     * @param listAppender 日志采集器
     * @param exceptionClass 异常类型
     * @return 是否包含
     */
    private boolean containsThrowable(
        ListAppender<ILoggingEvent> listAppender,
        Class<? extends Throwable> exceptionClass) {
        return listAppender.list.stream()
            .map(ILoggingEvent::getThrowableProxy)
            .anyMatch(throwableProxy -> throwableProxy != null
                && exceptionClass.getName().equals(throwableProxy.getClassName()));
    }
}
