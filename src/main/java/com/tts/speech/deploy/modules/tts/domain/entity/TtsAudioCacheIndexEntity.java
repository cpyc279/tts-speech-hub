package com.tts.speech.deploy.modules.tts.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TTS 音频缓存索引实体。
 *
 * @author yangchen5
 * @since 2026-03-23 15:30:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsAudioCacheIndexEntity {

    /**
     * 音频缓存键。
     */
    private String cacheKey;

    /**
     * 创建时间戳。
     */
    private Long createdAt;

    /**
     * 最近访问时间戳。
     */
    private Long lastAccessedAt;

    /**
     * 过期时间戳。
     */
    private Long expireAt;
}
