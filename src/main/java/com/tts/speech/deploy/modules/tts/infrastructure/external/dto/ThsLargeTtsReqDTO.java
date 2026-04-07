package com.tts.speech.deploy.modules.tts.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同花顺大模型 TTS 请求 DTO。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThsLargeTtsReqDTO {

    /**
     * 音色标识。
     */
    @NotNull
    @JsonProperty("voiceId")
    private String voiceId;

    /**
     * 待合成文本。
     */
    @NotNull
    @JsonProperty("text")
    private String text;
}
