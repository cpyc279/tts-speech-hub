package com.tts.speech.deploy.modules.tts.domain.entity;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TTS 响应领域实体。
 *
 * @author yangchen5
 * @since 2026-03-06 21:10:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsResponseEntity {

    /**
     * 音频结果列表。
     */
    private List<TtsAudioEntity> audioList;
}
