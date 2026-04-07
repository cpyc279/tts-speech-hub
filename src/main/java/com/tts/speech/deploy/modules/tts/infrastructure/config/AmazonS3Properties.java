package com.tts.speech.deploy.modules.tts.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Amazon S3 配置。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Data
@ConfigurationProperties(prefix = "amazon.s3")
public class AmazonS3Properties {

    /**
     * S3 桶名称。
     */
    private String bucketName;
    /**
     * S3 接入端点。
     */
    private String endpoint;
    /**
     * S3 对外访问域名。
     */
    private String domain;
    /**
     * S3 访问密钥。
     */
    private String accessKey;
    /**
     * S3 私有密钥。
     */
    private String secretKey;
}
