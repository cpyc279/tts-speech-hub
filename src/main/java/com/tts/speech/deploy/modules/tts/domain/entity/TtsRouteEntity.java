package com.tts.speech.deploy.modules.tts.domain.entity;

import com.tts.speech.deploy.common.enums.TtsEndpointEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TTS 路由实体。
 *
 * @author yangchen5
 * @since 2026-03-06 21:10:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsRouteEntity {

    /**
     * 模型编码。
     */
    private String modelCode;

    /**
     * 首选端点。
     */
    private TtsEndpointEnum primaryEndpoint;

    /**
     * 备用端点。
     */
    private TtsEndpointEnum fallbackEndpoint;
}
