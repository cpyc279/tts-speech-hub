package com.tts.speech.deploy.modules.tts.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新版 NER 大模型请求 DTO。
 *
 * @author yangchen5
 * @since 2026-03-26 00:00:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NerLargeModelReqDTO {

    /**
     * 调用模式。
     */
    private String mode;

    /**
     * 工作流标识。
     */
    @JsonProperty("pipe_name")
    private String pipeName;

    /**
     * 工作流输入变量。
     */
    @JsonProperty("input_variable_value")
    private InputVariableValue inputVariableValue;

    /**
     * 工作流输入变量。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InputVariableValue {

        /**
         * 待识别文本。
         */
        private String query;
    }
}
