package com.tts.speech.deploy.common.exception;

import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import lombok.Getter;

/**
 * 通用业务异常。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer errorCode;
    private final String errorMessage;

    /**
     * 构造业务异常。
     *
     * @param errorCodeEnum 错误码枚举
     */
    public BizException(ErrorCodeEnum errorCodeEnum) {
        super(errorCodeEnum.getMessage());
        this.errorCode = errorCodeEnum.getCode();
        this.errorMessage = errorCodeEnum.getMessage();
    }

    /**
     * 构造业务异常。
     *
     * @param errorCodeEnum 错误码枚举
     * @param message 异常消息
     */
    public BizException(ErrorCodeEnum errorCodeEnum, String message) {
        super(message);
        this.errorCode = errorCodeEnum.getCode();
        this.errorMessage = message;
    }
}
