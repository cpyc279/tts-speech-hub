package com.tts.speech.deploy.common.exception;

import com.tts.speech.deploy.app.dto.ApiResponseDTO;
import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import com.tts.speech.deploy.common.util.TraceIdUtil;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * @author yangchen5
 * @since 2026-03-06 19:42:00
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数校验失败默认提示语。
     */
    private static final String PARAMETER_VALIDATE_FAILED_MESSAGE = "参数校验失败";

    /**
     * 首条校验错误索引。
     */
    private static final int FIRST_ERROR_INDEX = 0;

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @return 错误响应
     */
    @ExceptionHandler(BizException.class)
    public ApiResponseDTO<Void> handleBizException(BizException exception) {
        // 业务异常已携带明确的错误码和错误信息，这里直接透传给调用方。
        return ApiResponseDTO.failed(exception.getErrorCode(), exception.getErrorMessage());
    }

    /**
     * 处理 NER Feign 调用异常。
     *
     * @param exception NER Feign 调用异常
     * @return 错误响应
     */
    @ExceptionHandler(NerFeignException.class)
    public ApiResponseDTO<Void> handleNerFeignException(NerFeignException exception) {
        // NER Feign 异常同样带有独立错误码和错误信息，统一按业务异常协议返回。
        return ApiResponseDTO.failed(exception.getErrorCode(), exception.getErrorMessage());
    }

    /**
     * 处理请求体参数校验异常。
     *
     * @param exception 参数校验异常
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponseDTO<Void> handleValidationException(MethodArgumentNotValidException exception) {
        // 请求体校验失败时优先返回首条错误，方便调用方快速修正入参。
        return ApiResponseDTO.failed(
            ErrorCodeEnum.INVALID_PARAMETER.getCode(),
            exception.getBindingResult().getAllErrors().get(FIRST_ERROR_INDEX).getDefaultMessage());
    }

    /**
     * 处理方法参数校验异常。
     *
     * @param exception 方法参数校验异常
     * @return 错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponseDTO<Void> handleConstraintViolationException(ConstraintViolationException exception) {
        // 方法参数校验失败时提取首条约束违规信息，保持返回格式简洁稳定。
        return ApiResponseDTO.failed(
            ErrorCodeEnum.INVALID_PARAMETER.getCode(),
            exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse(PARAMETER_VALIDATE_FAILED_MESSAGE));
    }

    /**
     * 处理兜底异常。
     *
     * @param exception 系统异常
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ApiResponseDTO<Void> handleException(Exception exception) {
        // 兜底异常除了返回统一错误码，还要输出链路上下文和异常堆栈，便于排查问题。
        String errorMessage = resolveExceptionMessage(exception);
        log.error(
            "系统异常兜底处理，traceId={}, errorMessage={}",
            TraceIdUtil.getCurrentTraceId(),
            errorMessage,
            exception);
        // 接口响应保留系统错误码，但 message 返回真实异常信息，避免排查时信息丢失。
        return ApiResponseDTO.failed(ErrorCodeEnum.SYSTEM_ERROR.getCode(), errorMessage);
    }

    /**
     * 解析兜底异常消息。
     *
     * @param exception 异常对象
     * @return 异常消息
     */
    private static String resolveExceptionMessage(Exception exception) {
        // 优先使用异常自带 message，避免兜底场景下返回空文案。
        if (StringUtils.hasText(exception.getMessage())) {
            return exception.getMessage();
        }
        return exception.getClass().getSimpleName();
    }
}
