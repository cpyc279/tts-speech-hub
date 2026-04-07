package com.tts.speech.deploy.modules.tts.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TTS 预处理文本实体。
 *
 * @author yangchen5
 * @since 2026-03-06 21:10:00
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TtsPreparedTextEntity {

    /**
     * 文本顺序号。
     */
    private Integer sequence;

    /**
     * 原始文本。
     */
    private String rawText;

    /**
     * 最终文本。
     */
    private String finalText;

    /**
     * 是否包含 name 标签。
     */
    private Boolean hasNameTag;

    /**
     * 缓存键。
     */
    private String cacheKey;

    /**
     * 是否在预处理阶段命中音频缓存。
     */
    private Boolean audioCacheHit;

    /**
     * 预处理阶段命中的 Base64 音频。
     */
    private String cachedBase64Audio;

    /**
     * 预处理阶段命中的音频 URL。
     */
    private String cachedAudioUrl;

    /**
     * 是否需要调用厂商 TTS 合成。
     */
    private Boolean needSynthesize;
}
