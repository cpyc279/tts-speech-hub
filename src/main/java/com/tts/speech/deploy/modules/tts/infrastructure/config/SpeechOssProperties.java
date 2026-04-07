package com.tts.speech.deploy.modules.tts.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音服务 OSS 配置。
 *
 * @author yangchen5
 * @since 2026-03-06 21:00:00
 */
@Data
@ConfigurationProperties(prefix = "kaihu.speech.oss")
public class SpeechOssProperties {

    /**
     * 对象存储目录前缀。
     */
    private String objectPrefix;

    /**
     * 是否拼接日期目录。
     */
    private Boolean appendDatePath;
}
