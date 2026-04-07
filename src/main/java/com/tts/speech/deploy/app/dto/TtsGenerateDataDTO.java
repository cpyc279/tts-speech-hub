package com.tts.speech.deploy.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * TTS 生成返回数据 DTO。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsGenerateDataDTO {

    /**
     * 音频结果列表。
     */
    @JsonProperty("audio_list")
    private List<TtsAudioDTO> audioList;
}
