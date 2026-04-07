package com.tts.speech.deploy.modules.tts.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TTS 音频缓存实体。
 *
 * @author yangchen5
 * @since 2026-03-18 22:10:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsAudioCacheEntity {

    /**
     * 音频缓存创建时间戳。
     */
    private Long createdAt;

    /**
     * Base64 音频内容。
     */
    private String base64Audio;
}
