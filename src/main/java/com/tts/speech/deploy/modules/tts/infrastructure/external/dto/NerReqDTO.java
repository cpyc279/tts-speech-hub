package com.tts.speech.deploy.modules.tts.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * NER 请求 DTO。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NerReqDTO {

    /**
     * 待识别命名实体的文本内容。
     */
    private String text;

    /**
     * 实体配置类型
     */
    private Schema schema;


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Schema{

        /**
         * 人名
         */
        @JsonProperty("人名")
        private String personName;
    }
}
