package com.tts.speech.deploy.common.exception;

import com.tts.speech.deploy.common.enums.ErrorCodeEnum;

/**
 * 参数无效异常。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
public class InvalidParameterException extends BizException {

    /**
     * 构造参数异常。
     *
     * @param message 异常消息
     */
    public InvalidParameterException(String message) {
        super(ErrorCodeEnum.INVALID_PARAMETER, message);
    }
}
