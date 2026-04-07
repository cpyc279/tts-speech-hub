package com.tts.speech.deploy.modules.tts.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音服务 NER 配置。
 *
 * @author yangchen5
 * @since 2026-03-06 21:00:00
 */
@Data
@ConfigurationProperties(prefix = "kaihu.speech.ner")
public class SpeechNerProperties {

    /**
     * NER 服务地址。
     */
    private String url;

    /**
     * NER 调用超时时间，单位毫秒。
     */
    private Integer timeoutMs;

    /**
     * NER 接口路径。
     */
    private String path;

    /**
     * NER 模板缓存配置。
     */
    private TemplateConfig template = new TemplateConfig();

    /**
     * 新版 NER 大模型配置。
     */
    private LargeModelConfig largeModel = new LargeModelConfig();

    /**
     * NER 模板缓存配置。
     */
    @Data
    public static class TemplateConfig {

        /**
         * 模板允许直接匹配的最小命中次数。
         */
        private Integer matchThreshold;

        /**
         * 模板生存周期，单位天。
         */
        private Long ttlDays;

        /**
         * 单个 broker 最多保留的模板数量。
         */
        private Integer maxTemplateCountPerBroker;
    }

    /**
     * 新版 NER 大模型配置。
     */
    @Data
    public static class LargeModelConfig {

        /**
         * 新版 NER 服务地址。
         */
        private String url;

        /**
         * 新版 NER 接口路径。
         */
        private String path;

        /**
         * 新版 NER 调用超时时间，单位毫秒。
         */
        private Integer timeoutMs;

        /**
         * 工作流标识。
         */
        private String pipeName;

        /**
         * 调用模式。
         */
        private String mode;

        /**
         * 调用方服务名称。
         */
        private String remoteSvc;

        /**
         * 调用方 IP。
         */
        private String remoteIp;
    }
}
