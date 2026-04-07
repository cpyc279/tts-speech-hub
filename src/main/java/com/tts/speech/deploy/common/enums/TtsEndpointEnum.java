package com.tts.speech.deploy.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 同花顺 TTS 接口枚举。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Getter
@AllArgsConstructor
public enum TtsEndpointEnum {

    /**
     * 小模型接口。
     */
    SMALL("SMALL"),
    /**
     * 大模型接口。
     */
    LARGE("LARGE");

    /**
     * 接口编码。
     */
    private final String code;

    /**
     * 根据编码解析枚举。
     *
     * @param code 接口编码
     * @return TTS 接口枚举
     */
    public static TtsEndpointEnum fromCode(String code) {
        for (TtsEndpointEnum endpointEnum : values()) {
            if (endpointEnum.code.equalsIgnoreCase(code)) {
                return endpointEnum;
            }
        }
        return null;
    }
}
