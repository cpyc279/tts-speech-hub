package com.tts.speech.deploy.modules.tts.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同花顺小模型 TTS 响应 DTO。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThsSmallTtsRespDTO {

    /**
     * 业务状态码。
     */
    private Integer code;

    /**
     * 状态说明。
     */
    private String note;

    /**
     * 响应数据体。
     */
    private DataPayload data;

    /**
     * 响应数据体。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPayload {

        /**
         * Base64 音频内容。
         */
        @JsonProperty("voiceData")
        private String voiceData;

        /**
         * 日志标识。
         */
        @JsonProperty("logId")
        private String logId;
    }
}
