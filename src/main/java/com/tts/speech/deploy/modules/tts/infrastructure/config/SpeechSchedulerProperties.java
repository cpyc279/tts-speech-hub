package com.tts.speech.deploy.modules.tts.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音服务调度配置。
 *
 * @author yangchen5
 * @since 2026-03-06 21:00:00
 */
@Data
@ConfigurationProperties(prefix = "kaihu.speech.scheduler")
public class SpeechSchedulerProperties {

    /**
     * 巡检任务配置。
     */
    private InspectJobConfig inspectJob = new InspectJobConfig();

    /**
     * 巡检任务配置项。
     */
    @Data
    public static class InspectJobConfig {

        /**
         * 是否启用巡检任务。
         */
        private Boolean enabled;

        /**
         * 巡检任务 Cron 表达式。
         */
        private String cron;

        /**
         * 单次任务批处理条数。
         */
        private Integer batchSize;

        /**
         * 比对时是否忽略标点。
         */
        private Boolean ignorePunctuation;

        /**
         * 比对时是否忽略空白。
         */
        private Boolean ignoreWhitespace;

        /**
         * 比对时是否忽略大小写。
         */
        private Boolean ignoreCase;
    }
}
