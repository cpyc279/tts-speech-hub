package com.tts.speech.deploy.modules.tts.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TTS 下游响应实体。
 *
 * @author yangchen5
 * @since 2026-03-06 21:10:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsVendorResponseEntity {

    /**
     * 音频 Base64。
     */
    private String base64Audio;

    /**
     * 下游追踪 ID。
     */
    private String vendorTraceId;

    /**
     * 实际用于下游合成的文本。
     */
    private String synthesizeText;
}
