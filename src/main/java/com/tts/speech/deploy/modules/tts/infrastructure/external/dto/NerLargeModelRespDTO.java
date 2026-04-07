package com.tts.speech.deploy.modules.tts.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新版 NER 大模型响应 DTO。
 *
 * @author yangchen5
 * @since 2026-03-26 00:00:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NerLargeModelRespDTO {

    /**
     * 返回消息。
     */
    @JsonProperty("status_msg")
    private String statusMsg;

    /**
     * 返回状态码。
     */
    @JsonProperty("status_code")
    private Integer statusCode;

    /**
     * 业务响应体。
     */
    private Response response;

    /**
     * token 使用信息。
     */
    private Usage usage;

    /**
     * 耗时。
     */
    @JsonProperty("cost_time")
    private Long costTime;

    /**
     * 业务响应体。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {

        /**
         * 固定 JSON 字符串。
         */
        private String text;
    }

    /**
     * token 使用信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {

        /**
         * 输出 token 数。
         */
        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        /**
         * 输入 token 数。
         */
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        /**
         * 使用类型。
         */
        private String type;
    }
}
