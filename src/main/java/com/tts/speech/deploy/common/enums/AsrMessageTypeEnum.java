package com.tts.speech.deploy.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ASR 消息类型枚举。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Getter
@AllArgsConstructor
public enum AsrMessageTypeEnum {

    /**
     * 文本消息。
     */
    TEXT("TEXT"),
    /**
     * 二进制消息。
     */
    BINARY("BINARY");

    /**
     * 消息类型编码。
     */
    private final String code;
}
