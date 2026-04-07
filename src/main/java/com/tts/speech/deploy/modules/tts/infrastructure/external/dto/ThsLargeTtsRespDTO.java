package com.tts.speech.deploy.modules.tts.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同花顺大模型 TTS 响应 DTO。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThsLargeTtsRespDTO {

    /**
     * 业务状态码。
     */
    private Integer code;

    /**
     * 错误类型。
     */
    private String errorType;

    /**
     * 状态说明。
     */
    private String msg;

    /**
     * 调用链路追踪标识。
     */
    private String traceId;

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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataPayload {

        /**
         * Base64 音频内容。
         */
        @JsonProperty("audio")
        private String audio;
    }
}
