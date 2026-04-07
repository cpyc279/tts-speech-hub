package com.tts.speech.deploy.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TTS 音频响应 DTO。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsAudioDTO {

    /**
     * 原始文本。
     */
    @JsonProperty("raw_text")
    private String rawText;

    /**
     * 最终用于合成的文本。
     */
    @JsonProperty("final_text")
    private String finalText;

    /**
     * Base64 音频内容。
     */
    @JsonProperty("base64_audio")
    private String base64Audio;

}
