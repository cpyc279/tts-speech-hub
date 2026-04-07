package com.tts.speech.deploy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 开户语音转发服务启动类。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@EnableFeignClients
@ConfigurationPropertiesScan
@SpringBootApplication
public class TtsSpeechApplication {

    /**
     * 启动应用程序。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(TtsSpeechApplication.class, args);
    }
}
