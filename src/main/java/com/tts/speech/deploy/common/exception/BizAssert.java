package com.tts.speech.deploy.common.exception;

import com.tts.speech.deploy.common.enums.ErrorCodeEnum;

/**
 * 业务断言工具。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
public final class BizAssert {

    private BizAssert() {
    }

    /**
     * 断言条件为真。
     *
     * @param expression 断言表达式
     * @param errorCodeEnum 错误码
     * @param message 异常消息
     */
    public static void isTrue(boolean expression, ErrorCodeEnum errorCodeEnum, String message) {
        if (!expression) {
            throw new BizException(errorCodeEnum, message);
        }
    }

    /**
     * 断言对象非空。
     *
     * @param object 断言对象
     * @param errorCodeEnum 错误码
     * @param message 异常消息
     */
    public static void notNull(Object object, ErrorCodeEnum errorCodeEnum, String message) {
        if (object == null) {
            throw new BizException(errorCodeEnum, message);
        }
    }
}
