package com.tts.speech.deploy.common.exception;

import com.tts.speech.deploy.common.enums.ErrorCodeEnum;

/**
 * 任务不存在异常。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
public class TaskNotExistException extends BizException {

    /**
     * 构造任务不存在异常。
     *
     * @param message 异常消息
     */
    public TaskNotExistException(String message) {
        super(ErrorCodeEnum.INVALID_PARAMETER, message);
    }
}
