package com.tts.speech.deploy.modules.tts.infrastructure.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.tts.speech.deploy.common.constant.CommonConstant;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Amazon S3 自动配置。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Configuration
@EnableConfigurationProperties({AmazonS3Properties.class})
public class AmazonS3Config {

    private static final String S3_SIGNER_TYPE = "S3SignerType";

    /**
     * 创建 AmazonS3 客户端。
     *
     * @param amazonS3Properties S3 配置
     * @return AmazonS3 客户端
     */
    @Bean
    public AmazonS3 amazonS3(AmazonS3Properties amazonS3Properties) {
        BasicAWSCredentials credentials =
            new BasicAWSCredentials(amazonS3Properties.getAccessKey(), amazonS3Properties.getSecretKey());
        ClientConfiguration configuration = new ClientConfiguration();
        configuration.setProtocol(Protocol.HTTP);
        configuration.setSignerOverride(S3_SIGNER_TYPE);
        AwsClientBuilder.EndpointConfiguration endpointConfiguration =
            new AwsClientBuilder.EndpointConfiguration(
                CommonConstant.HTTP_PREFIX + amazonS3Properties.getEndpoint(),
                Regions.CN_NORTH_1.getName());
        return AmazonS3ClientBuilder.standard()
            .withClientConfiguration(configuration)
            .withEndpointConfiguration(endpointConfiguration)
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .withPathStyleAccessEnabled(true)
            .build();
    }
}
