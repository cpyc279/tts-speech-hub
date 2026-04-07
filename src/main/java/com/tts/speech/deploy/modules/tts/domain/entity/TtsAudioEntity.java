package com.tts.speech.deploy.modules.tts.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TTS 音频实体。
 *
 * @author yangchen5
 * @since 2026-03-06 21:10:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsAudioEntity {

    /**
     * 文本顺序号。
     */
    private Integer sequence;

    /**
     * 原始文本。
     */
    private String rawText;

    /**
     * 处理后的最终文本。
     */
    private String finalText;

    /**
     * 实际用于合成的文本。
     */
    private String synthesizeText;

    /**
     * 音频 Base64 内容。
     */
    private String base64Audio;

    /**
     * OSS 音频访问地址。
     */
    private String audioUrl;

    /**
     * 音频缓存键。
     */
    private String cacheKey;

    /**
     * 是否包含 name 标签。
     */
    private Boolean hasNameTag;

    /**
     * 下游追踪 ID。
     */
    private String vendorTraceId;

    /**
     * 是否命中音频缓存。
     */
    private Boolean cacheHit;
}
