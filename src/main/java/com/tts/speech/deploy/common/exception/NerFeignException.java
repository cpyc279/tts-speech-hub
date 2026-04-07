package com.tts.speech.deploy.common.exception;

import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import lombok.Getter;

/**
 * NER Feign 调用异常。
 *
 * @author yangchen5
 * @since 2026-03-17 21:11:52
 */
@Getter
public class NerFeignException extends RuntimeException {

    /**
     * 业务错误码。
     */
    private final Integer errorCode;

    /**
     * 业务错误信息。
     */
    private final String errorMessage;

    /**
     * 构造 NER Feign 调用异常。
     *
     * @param errorCodeEnum 错误码枚举
     */
    public NerFeignException(ErrorCodeEnum errorCodeEnum) {
        // 直接复用错误码枚举中的标准错误信息，保证异常语义与统一错误码一致。
        super(errorCodeEnum.getMessage());
        // 将错误码写入异常对象，便于全局异常处理器直接透传。
        this.errorCode = errorCodeEnum.getCode();
        // 将错误信息写入异常对象，避免外层再次拼装消息。
        this.errorMessage = errorCodeEnum.getMessage();
    }

    /**
     * 构造带原因的 NER Feign 调用异常。
     *
     * @param errorCodeEnum 错误码枚举
     * @param cause 异常原因
     */
    public NerFeignException(ErrorCodeEnum errorCodeEnum, Throwable cause) {
        // 使用统一错误信息并保留原始异常堆栈，便于定位真实失败原因。
        super(errorCodeEnum.getMessage(), cause);
        // 将错误码写入异常对象，便于全局异常处理器直接透传。
        this.errorCode = errorCodeEnum.getCode();
        // 继续使用统一错误信息，避免下游异常消息污染对外协议。
        this.errorMessage = errorCodeEnum.getMessage();
    }

    /**
     * 构造自定义消息的 NER Feign 调用异常。
     *
     * @param errorCodeEnum 错误码枚举
     * @param message 异常消息
     * @param cause 异常原因
     */
    public NerFeignException(ErrorCodeEnum errorCodeEnum, String message, Throwable cause) {
        // 在需要自定义错误文案时保留原始异常堆栈，兼顾排查和对外提示。
        super(message, cause);
        // 将错误码写入异常对象，便于全局异常处理器直接透传。
        this.errorCode = errorCodeEnum.getCode();
        // 保存自定义错误文案，供全局异常处理器返回给调用方。
        this.errorMessage = message;
    }
}
