package com.tts.speech.deploy.common.config;

import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音服务鉴权配置。
 *
 * @author yangchen5
 * @since 2026-03-09 21:36:00
 */
@Data
@ConfigurationProperties(prefix = "kaihu.speech.auth")
public class SpeechAuthProperties {

    /**
     * 是否启用客户端鉴权。
     */
    private Boolean enabled = Boolean.FALSE;

    /**
     * 时间戳允许偏差秒数。
     */
    private Long timestampToleranceSeconds = 300L;

    /**
     * 全局兜底密钥。
     */
    private String globalSecret;

    /**
     * 券商密钥映射。
     */
    private Map<String, String> brokerSecrets;

    /**
     * 后台缓存管理固定 token。
     */
    private String adminToken;
}
